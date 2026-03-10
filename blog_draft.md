# [Fullstack] AI 분석 상태 업데이트: Polling에서 WebSocket(STOMP)으로 전환하기

AI 분석(OCR 및 이력서 데이터 추출)과 같은 비동기 작업의 진행 상태를 사용자에게 어떻게 실시간으로 전달할 수 있을까요? 
가장 쉬운 방법은 **Polling**이지만, 이는 서버 자원 낭비와 지연 시간(Latency)이라는 한계를 가지고 있습니다. 

이번에는 Spring Boot의 **STOMP WebSocket**을 도입하여 Polling을 제거하고, 진정한 실시간 이벤트를 구현하며 사용자 경험(UX)을 극대화한 과정을 공유합니다.

## 1. 전/후 아키텍처 비교

### Before (Polling)
- **Frontend**: 1초마다 `/api/analysis/{id}` 호출 (GET)
- **Backend**: DB에서 상태 조회 후 응답
- **문제점**: 1초의 지연 발생, 불필요한 트래픽 및 DB 커넥션 소모

### After (WebSocket STOMP)
- **Backend**: 분석 상태 조각(STARTED, PROCESSING, COMPLETED)이 발생할 때마다 전용 Topic으로 Push
- **Frontend**: WebSocket 연결 후 Topic 구독. 메시지 수신 즉시 UI 업데이트
- **장점**: Zero-Latency, 서버 부하 감소, 부드러운 애니메이션 구현 가능

## 2. 주요 구현 사항 (Backend)

### WebSocket 설정 (Config)
`WebSocketMessageBrokerConfigurer`를 통해 `/ws` 엔드포인트를 개방하고 애플리케이션 목적지를 설정합니다.

### 이벤트 리스너 (EventListener)
`SimpMessagingTemplate`을 주입받아 특정 사용자에게만 상태 변화를 전송합니다.

```kotlin
private fun sendWebSocketUpdate(username: String, requestId: Long, status: String, message: String) {
    val payload = mapOf(
        "requestId" to requestId,
        "status" to status,
        "message" to message,
        "timestamp" to System.currentTimeMillis()
    )
    messagingTemplate.convertAndSendToUser(username, "/topic/analysis", payload)
}
```

## 3. 프리미엄 UI 및 UX 디자인 (Frontend)

Next.js 환경에서 `@stomp/stompjs`와 `sockjs-client`를 사용하여 실시간 데이터를 수신합니다.

- **디자인 컨셉**: Glassmorphism 스타일과 부드러운 프로그레스 애니메이션 적용.
- **실시간 로그**: "파일 다운로드 중...", "OCR 분석 수행 중..." 등 백엔드에서 전송하는 세부 메시지를 실시간으로 출력하여 사용자의 체감 대기 시간을 줄였습니다.

## 4. 셀프 힐링 (Self-Healing) & 테스트

Kotlin 특유의 **Strict Null-Safety**와 Mockito의 충돌 문제를 `any() ?: fallback` 패턴으로 해결하고, `OcrService`를 `open` 클래스로 전환하여 100% 유닛 테스트 통과를 달성했습니다.

## 결론

단순히 기능을 만드는 것을 넘어, 시스템의 효율성과 사용자의 '와우 포인트'를 위해 WebSockets을 도입했습니다. 이제 분석 작업이 시작되자마자 사용자는 즉각적인 반응을 확인할 수 있습니다.

---
**Tag**: #Spring #Kotlin #Nextjs #WebSocket #STOMP #Fullstack #Developer #AIBlog
