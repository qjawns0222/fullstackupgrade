# [Fullstack] 실시간 알림 (WebSocket) + 배치 작업 연동 - 더 이상의 폴링은 없다

## 1. 멍하니 모니터만 바라보는 것은 죄악이다
배치 작업은 시스템의 필수 요소지만, 그 완료 시점을 알기 위해 DB를 조회하거나 로그를 새로고침하는 행위는 개발자의 리소스를 낭비하는 가장 멍청한 짓이다. 기존 레거시 코드에는 이런 '기다림'을 해결할 장치가 전무했다. 이메일 알림은 느리고, 스팸함에 처박히기 일쑤다. 즉시성이 보장되지 않는 알림은 알림이 아니다.

## 2. 왜 WebSocket인가? (Zero-Base Analysis)
물론 SSE(Server-Sent Events)도 고려해볼 만했다. 하지만 양방향 통신 가능성을 열어두고, STOMP 프로토콜을 통해 메시지 브로커 패턴을 정석적으로 구현하는 것이 확장성 면에서 유리하다. HTTP 폴링? 그건 서버 자원을 하수구에 버리는 짓이다. 우리는 '연결'이 필요하다.

## 3. 구현: Spring Batch와 WebSocket의 만남
백엔드에서는 `spring-boot-starter-websocket`을 도입했다. 핵심은 `SimpMessagingTemplate`을 배치 리스너(`JobCompletionNotificationListener`)에 주입하는 것이다. 배치가 성공적으로 끝나면(`BatchStatus.COMPLETED`), 즉시 `/topic/notifications` 토픽으로 메시지를 쏜다.

```kotlin
// JobCompletionNotificationListener.kt
override fun afterJob(jobExecution: JobExecution) {
    if (jobExecution.status == BatchStatus.COMPLETED) {
        template.convertAndSend("/topic/notifications", NotificationMessage("Batch Job Completed!"))
    }
}
```

프론트엔드(Next.js)에서는 `sockjs-client`와 `@stomp/stompjs`를 사용해 우아하게 신호를 받는다. 기존에 어설프게 작성되어 있던 `EventSource` 기반의 코드는 가차 없이 폐기했다. 

```typescript
// useNotification.ts
const socket = new SockJS('http://localhost:8000/ws');
const client = new Client({
    webSocketFactory: () => socket,
    onConnect: () => {
        client.subscribe('/topic/notifications', (msg) => {
            dispatchToast(JSON.parse(msg.body).message, 'success');
        });
    }
});
```

게이트웨이(Spring Cloud Gateway) 설정도 잊지 말아야 한다. `/ws/**` 경로에 대한 라우팅을 추가해주지 않으면 프론트엔드는 404 에러만 뱉을 것이다.

## 4. 마치며
기술은 사용자를 편하게 해야 한다. 그리고 시스템 관리자도, 개발자도 결국은 사용자다. 내 시간을 아껴주는 기술이 진짜 기술이다. 이제 배치 돌려놓고 커피 한 잔 하러 가도 된다. 알림이 올 테니까.
