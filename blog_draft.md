# [Fullstack] Redis 기반 API 멱등성(Idempotency) 보장 컴포넌트 개발

## 1. 개발 배경 (Why)
분산 시스템 환경이나 네트워크 지연이 있는 모바일 환경에서, 사용자가 버튼을 중복 클릭하거나 네트워크 재시도로 인해 동일한 요청이 서버로 여러 번 전송되는 일은 흔하다. 특히 결제나 포인트 차감 같은 민감한 로직은 **단 한 번만 실행되어야 하는(Exactly-Once) 멱등성(Idempotency)** 보장이 필수적이다. 비즈니스 로직마다 중복 방지 코드를 넣는 것은 비효율적이기에, 오늘은 **AOP(Aspect Oriented Programming)** 와 **Redis** 를 활용해 범용적으로 사용할 수 있는 멱등성 처리기를 구현했다.

## 2. 설계 및 기술 스택 (Architecture)
### 핵심 기술
- **Kotlin & Spring AOP**: 비즈니스 로직 침투를 최소화하기 위해 어노테이션 기반 Aspect로 구현.
- **Redis (StringRedisTemplate)**: 여러 서버 인스턴스 간에도 상태를 공유하고, TTL 기능을 활용해 키를 자동 만료시키기 위함.
- **Custom Annotation**: `@Idempotent` 어노테이션만 붙이면 동작하도록 설계.

### 동작 원리
1. 클라이언트는 요청 헤더에 `Idempotency-Key` (UUID 등)를 포함하여 요청한다.
2. `IdempotencyAspect` 가 요청을 가로채 Redis에 해당 키가 존재하는지 확인한다.
3. **키가 없으면**: Redis에 키를 저장(SET)하고 비즈니스 로직을 수행한다.
4. **키가 있으면**: 중복 요청으로 간주하고 `IdempotencyException` 예외를 던져 409 Conflict 등의 응답을 반환한다.
5. 로직 수행 중 예외가 발생하면 Redis 키를 삭제하여 재시도를 허용한다.

## 3. 주요 구현 내용 (Implementation)

### 3.1 Custom Annotation 정의
```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Idempotent(
    val expireTime: Long = 60,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
    val keyHeader: String = "Idempotency-Key"
)
```
개발자가 만료 시간과 헤더 키를 자유롭게 설정할 수 있도록 유연하게 설계했다.

### 3.2 AOP Aspect 구현
`RedisTemplate.opsForValue().setIfAbsent(...)` 같은 원자적 연산을 활용할 수도 있지만, 이번 구현에서는 명시적인 키 체크와 예외 처리를 위해 `hasKey` 체크 후 `set` 하는 방식을 택했다. 실제 운영 환경에서는 동시성 이슈를 완벽히 제어하기 위해 `SETNX` 패턴을 사용하는 것이 더 안전할 것이다.

### 3.3 테스트 (Testing)
Mockito를 활용하여 `StringRedisTemplate`을 Mocking하고, 키 유무에 따른 Aspect의 분기 처리를 단위 테스트로 철저히 검증했다. 또한 Next.js 프론트엔드에 테스트 페이지를 만들어 실제로 여러 요청을 동시에 날렸을 때 중복 요청이 차단되는지 확인했다.

## 4. 마치며 (Retrospective)
이런 '횡단 관심사(Cross-cutting Concern)'를 인프라 계층으로 분리해두면, 동료 개발자들은 비즈니스 로직에만 집중할 수 있다. 이것이 바로 시니어 개발자가 팀에 기여하는 방식이라 생각한다. Redis라는 강력한 도구가 있기에 상태 관리 복잡도를 크게 줄일 수 있었다. 다음에는 더 정교한 분산 락(Distributed Lock) 구현에도 도전해봐야겠다.
