# [Fullstack] RabbitMQ를 이용한 비동기 감사 로그(Audit Log) 처리

비동기 처리에 대한 고민은 언제나 트레이드오프와의 싸움이다.
기존에 구현해두었던 감사 로그(Audit Log) 기능은 `CompletableFuture.runAsync`를 사용해서 메인 로직의 블로킹을 막고 있었다. 
나쁘지 않은 선택이었지만, 트래픽이 몰리면 스레드 풀 큐가 터지거나 서버가 갑자기 죽었을 때 로그가 유실될 위험이 컸다.
특히 금융이나 중요 데이터를 다루는 시스템이라면 감사 로그의 유실은 치명적이다.

그래서 이번에는 메시지 큐의 대명사인 **RabbitMQ**를 도입해서 아키텍처를 개선해보기로 했다.

### 1. RabbitMQ 도입 배경
`CompletableFuture`는 메모리 기반이다. 서버가 재시작되면 큐에 있던 작업은 다 날아간다.
반면 RabbitMQ는 메시지를 디스크에 저장할 수도 있고, 컨슈머(Consumer)가 처리를 실패하면 다시 큐에 넣을 수도 있다 (물론 설정하기 나름이지만).
확실한 로그 저장을 위해 브로커를 하나 끼우는 구조로 변경했다.

### 2. 구현 과정
**Docker Compose**에 RabbitMQ 컨테이너를 추가하는 것부터 시작했다.
그리고 Spring Boot에 `spring-boot-starter-amqp` 의존성을 추가하고, `RabbitMqConfig` 설정을 잡았다.
가장 중요한 건 메시지 컨버터. Jackson을 사용해서 DTO를 JSON으로 직렬화해서 보내도록 설정했다.

**Producer & Consumer 패턴**
- **Producer**: AOP(`AuditLogAspect`)에서 DB를 직접 호출하는 대신, `AuditLogProducer`를 통해 RabbitMQ로 메시지를 쏜다.
- **Consumer**: `AuditLogConsumer`가 큐를 리스닝하고 있다가, 메시지가 오면 그때 비로소 DB(`AuditLogRepository`)에 저장한다.

이렇게 하면 트래픽이 폭주해도 RabbitMQ가 버퍼 역할을 해주고, DB 부하를 조절할 수 있다.

### 3. 트러블슈팅
테스트 코드를 작성하면서 꽤 애를 먹었다.
Mockito와 Kotlin을 같이 쓸 때 `any()` 매처가 `null`을 반환하는 문제 때문에 `NullPointerException`이 계속 발생했다.
`ArgumentCaptor`를 사용해서 값을 캡쳐하려고 했는데, 이 과정에서도 코틀린의 Null Safety가 발목을 잡았다.
결국 `doAnswer`를 사용해서 메서드 호출 시점에 인자를 가로채는 방식으로 테스트를 통과시켰다.
테스트 코드는 역시 꼼꼼하게 짜야 제맛이다.

### 4. 마치며
단순히 "비동기로 돌린다"는 것과 "안정적인 비동기 시스템을 구축한다"는 건 천지차이다.
RabbitMQ를 도입함으로써 시스템의 복잡도는 조금 올라갔지만, 데이터의 신뢰성과 시스템의 안정성은 비교할 수 없을 만큼 좋아졌다.
다음에는 Kafka로 대용량 로그 처리를 해보는 것도 재밌을 것 같다.
