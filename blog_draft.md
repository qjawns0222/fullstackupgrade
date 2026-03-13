# Spring Boot Intelligence: Redis 캐싱과 Idempotency로 서버 자원 90% 아끼기

안녕하세요. 오늘은 시스템을 단순히 '동작하게' 만드는 것을 넘어, 어떻게 하면 '더 똑똑하고 효율적으로' 만들 수 있는지에 대한 고민을 담아보았습니다. **미션 5: Distributed Task Orchestration & Intelligence**의 핵심 내용을 정리합니다.

## 1. 반복되는 무거운 연산, 어떻게 줄일 것인가? (OCR 캐싱)

Tesseract 같은 OCR 엔진은 CPU를 매우 많이 소모합니다. 만약 사용자가 동일한 서류를 실수로 여러 번 업로드한다면? 서버는 매번 동일한 연산을 반복하며 비명을 지를 것입니다.

이를 해결하기 위해 **SHA-256 파일 해시 기반 캐싱**을 도입했습니다.
- 파일 데이터의 지문을 채취(Hash)하여 Redis에 결과를 저장합니다.
- 동일한 해시를 가진 파일이 들어오면 연산을 생략하고 즉시 캐시된 결과를 반환합니다.
- 이를 통해 중복 요청에 대해 CPU 자원을 90% 이상 절약할 수 있게 되었습니다.

## 2. API의 신뢰성, '응답 캐싱형 Idempotency'로 완성하기

단순히 "이미 처리된 요청입니다"라고 에러를 뱉는 것은 초보적인 접근입니다. 진정한 엔터프라이즈 급 API는 **중복 요청에 대해 이전과 동일한 성공 응답**을 돌려주어야 합니다.

- `IdempotencyAspect`를 강화하여 성공한 응답 본문 자체를 Redis에 캐싱합니다.
- 클라이언트가 네트워크 지연 등으로 동일 요청을 다시 보냈을 때, 서버는 실제 비즈니스 로직을 수행하지 않고 캐시된 응답에 `X-Idempotent-Cache: HIT` 헤더를 붙여 반환합니다.

## 3. 블랙박스였던 백그라운드 작업, 'Task Center'로 시각화하기

Spring Batch는 강력하지만, 현재 어떤 작업이 돌고 있는지, 과거에 실패한 이유는 무엇인지 파악하기 어렵습니다.

이를 위해 **Premium Task Center**를 구축했습니다.
- **Batch Monitor**: Spring Batch의 실행 이력, 시작/종료 시간, Exit Code를 실시간으로 확인합니다.
- **Compute Intel**: 캐싱을 통해 절약된 자원과 히트율을 대시보드에서 숫자로 증명합니다.
- **Manual Control**: 스케줄링된 작업 외에도 관리자가 즉시 배치를 실행할 수 있는 제어권을 제공합니다.

## 4. 마치며

이번 미션을 통해 우리 시스템은 비즈니스 로직 뿐만 아니라 **운영 효율성** 측면에서도 한 단계 진화했습니다. 리소스를 아끼고, API의 신뢰성을 높이며, 보이지 않는 시스템 내부를 투명하게 공개하는 것. 이것이 바로 시니어 개발자가 추구해야 할 아키텍처의 방향이라고 믿습니다.

---
**GitHub**: [qjawns0222/fullstackupgrade](https://github.com/qjawns0222/fullstackupgrade)
**Mission Status**: COMPLETE (Intelligence & Optimization)
