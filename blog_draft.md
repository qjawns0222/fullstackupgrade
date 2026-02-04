
[Fullstack] Resilience4j Circuit Breaker 도입 - 메일 발송 장애 격리 및 시스템 안정성 강화

오늘은 프로젝트의 백엔드 안정성을 점검하던 중, MailService가 외부 SMTP 서버(Gmail)에 강한 의존성을 가지고 있다는 점을 발견했다. 외부 API나 서비스는 언제든 장애가 발생할 수 있고, 만약 응답이 지연된다면 우리 서버의 스레드까지 점유되어 전체 시스템의 장애로 번질 위험(Cascading Failure)이 있다.

이를 방지하기 위해 'Circuit Breaker(회로 차단기)' 패턴을 적용하기로 결정했다. 라이브러리로는 가주 사용되는 Hystrix 대신, 더 가볍고 함수형 프로그래밍을 지원하는 Resilience4j를 선택했다.

[작업 내용]

1. 의존성 추가 및 설정
Spring Boot 3 환경에 맞춰 resilience4j-spring-boot3 라이브러리를 추가했다.
단순히 라이브러리만 추가하는 것이 아니라, ResilienceConfig 클래스를 만들어 'mailService'라는 이름의 인스턴스에 대한 구체적인 정책을 정의했다.
- Sliding Window Size: 5 (최근 5번의 요청을 기준으로 판단)
- Failure Rate Threshold: 40% (5번 중 2번 이상 실패 시 회로 차단)
- Wait Duration: 20초 (차단 후 20초 대기 후 다시 시도)

2. 서비스 계층 적용
MailService의 sendWeeklyReport 메소드에 @CircuitBreaker 어노테이션을 적용했다.
중요한 점은 장애 발생 시의 처리다. fallbackMethod를 지정하여, 회로가 열려있거나 예외가 발생했을 때 단순히 에러를 뱉는 대신 'fallbackSendWeeklyReport' 메소드가 실행되도록 했다. 현재는 로그를 남기는 정도로 처리했지만, 추후에는 재시도 큐에 넣거나 관리자에게 알림을 보내는 식으로 확장할 수 있다.

3. 모니터링 및 프론트엔드 연동
백엔드 로직만으로는 현재 상태를 알기 어렵다. Spring Actuator 설정을 통해 circuitbreakers 엔드포인트를 노출시켰고, Next.js 프론트엔드에 관리자용 대시보드 페이지(/admin/status)를 추가했다.
이 대시보드에서는 현재 메일 서비스의 회로 상태(CLOSED, OPEN, HALF_OPEN)를 실시간으로 확인할 수 있고, 테스트 버튼을 통해 강제로 메일 발송을 시도해볼 수 있다.

[회고]
테스트 코드를 작성하는 과정에서 @SpringBootTest와 Elasticsearch 설정 간의 충돌로 인해 컨텍스트 로드 문제가 발생했다. 통합 테스트 환경을 구축할 때는 외부 의존성을 적절히 Mocking하는 것이 얼마나 중요한지 다시 한 번 느꼈다. 결국 비즈니스 로직을 검증하는 단위 테스트로 전환하여 배포 안정성을 확보했다.
이번 작업을 통해 외부 시스템 장애가 우리 서비스로 전파되는 것을 막는 '격벽'을 세웠다는 점에서 큰 성취감을 느낀다.
