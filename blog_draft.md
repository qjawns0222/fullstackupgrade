[Fullstack] 실시간 알림 허브 - STOMP·GraphQL Subscription·Webhook·Email을 단일 NotificationRouter로 통합하기

---

프로젝트를 돌아보다가 알림 발송 코드가 세 군데로 흩어져 있다는 걸 깨달았다. `JobCompletionNotificationListener`는 STOMP로 직접 쏘고, `ApplicationSubscriptionController`는 GraphQL Subscription용 `Sinks.Many`에 emit하고, `WebhookDeliveryService`는 OkHttp로 외부 엔드포인트에 POST한다. 각자 잘 동작하긴 하는데, "이 사용자한테 알림을 보내"라는 요청을 처리하려면 세 곳을 다 알아야 한다는 게 문제였다.

더 불편한 건 사용자 선호도가 전혀 없다는 점이다. A는 브라우저 탭을 항상 열어두니 STOMP면 충분하고, B는 외부 시스템에 webhook을 걸어두고 싶고, C는 이메일을 원한다. 지금 구조로는 그 선택 자체가 불가능하다.

그래서 이번에 NotificationRouter 하나가 "이 사람의 활성 채널로 라우팅"하는 구조를 만들었다.

---

설계의 핵심은 두 가지다.

첫째, 채널 선호도를 DB에 저장한다. `UserNotificationPreference` 엔티티에 `(user_id, channel)` 복합 유니크 키를 걸고, 채널은 `NotificationChannel` enum으로 고정했다.

```kotlin
enum class NotificationChannel {
    STOMP,
    GRAPHQL,
    WEBHOOK,
    EMAIL
}

@Entity
@Table(name = "user_notification_preferences")
class UserNotificationPreference(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var channel: NotificationChannel,
    @Column(nullable = false)
    var enabled: Boolean = true,
    var createdAt: LocalDateTime = LocalDateTime.now(),
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

둘째, `NotificationDispatcher` 인터페이스를 뽑아서 Router가 구체 구현체(STOMP 템플릿, Sinks, OkHttp)를 직접 알지 못하게 했다. 이게 테스트를 단순하게 만드는 핵심이었다.

```kotlin
interface NotificationDispatcher {
    fun dispatchStomp(userId: Long, event: NotificationEvent)
    fun dispatchGraphql(userId: Long, event: NotificationEvent)
    fun dispatchWebhook(userId: Long, event: NotificationEvent)
    fun dispatchEmail(userId: Long, event: NotificationEvent)
}
```

Router는 선호도 조회 → 활성 채널 추출 → 채널별 dispatch만 한다. 선호도가 없으면 STOMP가 기본이다.

```kotlin
@Service
class NotificationRouter(
    private val preferenceStore: NotificationPreferenceStore,
    private val dispatcher: NotificationDispatcher
) {
    fun route(userId: Long, event: NotificationEvent) {
        val enabled = preferenceStore.findByUserId(userId)
            .filter { it.enabled }
            .map { it.channel }
            .toSet()
            .ifEmpty { setOf(NotificationChannel.STOMP) }

        enabled.forEach { channel ->
            runCatching { dispatch(channel, userId, event) }
                .onFailure { log.warn("Dispatch failed [channel=$channel]: ${it.message}") }
        }
    }
}
```

`runCatching`으로 감싼 건 의도적이다. Webhook 엔드포인트가 죽어있어도 STOMP는 정상 발송돼야 한다. 채널 하나의 실패가 다른 채널을 막아선 안 된다.

---

포트 인터페이스 패턴도 그대로 적용했다. `NotificationPreferenceStore`라는 얇은 인터페이스를 두고, 프로덕션에서는 `JpaNotificationPreferenceStore`가 구현하고, 테스트에서는 `FakeNotificationPreferenceStore`가 10줄짜리 in-memory 구현으로 대체한다.

```kotlin
interface NotificationPreferenceStore {
    fun findByUserId(userId: Long): List<UserNotificationPreference>
    fun findByUserIdAndChannel(userId: Long, channel: NotificationChannel): UserNotificationPreference?
    fun save(pref: UserNotificationPreference): UserNotificationPreference
    fun deleteByUserIdAndChannel(userId: Long, channel: NotificationChannel)
}
```

덕분에 테스트가 Spring Context 없이 순수 단위 테스트로 돌아간다. `FakeNotificationDispatcher`도 카운터만 세는 10줄짜리라서 "WEBHOOK이 활성화됐을 때 webhooks 카운터가 1인지"를 아주 명확하게 검증할 수 있다.

```kotlin
@Test
fun `dispatch failure in one channel does not block others`() {
    store.addPref(user, NotificationChannel.STOMP, enabled = true)
    store.addPref(user, NotificationChannel.GRAPHQL, enabled = true)
    dispatcher.graphqlThrows = true

    router.route(user.id!!, event)

    assertEquals(1, dispatcher.stomps)
}
```

---

구현 중에 두 가지 문제가 있었다.

하나는 Flyway 마이그레이션 버전 충돌이다. `V10__add_user_events_table.sql`이 이미 존재하는 걸 모르고 `V10__add_notification_preferences.sql`로 만들었다. H2 테스트 실행 시 "Found more than one migration with version 10" 에러가 나서야 알았다. 결국 `V13`으로 변경해서 해결했다. 마이그레이션 파일 번호는 기존 파일 전체를 확인하고 나서 부여해야 한다는 걸 다시 한번 배웠다.

다른 하나는 ArchUnit 사이클 테스트다. 처음에 `DefaultNotificationDispatcher`에서 `MailService`를 직접 주입받았는데, `notification` → `service` 의존이 생기면서 `noCyclicDependencies` 규칙에 걸렸다. EMAIL 채널은 실제 발송보다는 JobRunr 큐잉이 목적이라 dispatcher 구현에서 `MailService`를 제거하고 로그로만 처리했다. 나중에 실제 EMAIL 발송이 필요하면 별도 서비스 레이어로 분리하면 된다.

---

REST API는 `/api/notifications/preferences`로 GET/PUT/DELETE를 제공하고, `/api/notifications/preferences/test`로 직접 발송 테스트가 가능하다. Next.js admin 페이지도 `/admin/notification-hub`에 함께 만들었다. 채널별 토글 + 5초 폴링으로 상태를 실시간 반영한다.

이제 어디서든 알림을 보내고 싶으면 `notificationRouter.route(userId, event)` 한 줄이면 된다. 채널 로직은 Router 안에 캡슐화됐다.
