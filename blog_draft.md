# [Dev Log] Spring Cloud Gateway 분석 및 Nginx와의 차이점

**날짜**: 2026.01.21

## 1. Gateway 코드 분석
오늘 구현된 Spring Cloud Gateway 코드를 뜯어봤다. 구조는 전형적인 SCG 패턴이다.

### 주요 구성
- **라우팅(`Routes`)**: `/api/**` 경로로 들어오는 모든 요청을 백엔드(`localhost:8080`)로 넘긴다.
- **필터(`LoggingFilter`)**: `GlobalFilter`와 `Ordered`를 구현했다.
    - 요청 진입 시 Path와 Method를 찍고, 응답 나갈 때 Status Code를 찍는다.
    - `Ordered.LOWEST_PRECEDENCE`로 설정되어 있어 필터 체인의 가장 마지막에 실행된다. 실무적으로 로깅은 가장 바깥에서 감싸는 게 맞다.
- **서킷 브레이커(`Resilience4j`)**: 백엔드가 죽었을 때 장애 전파를 막기 위해 설정됨. 실패율 50% 넘어가면 회로를 끊는다(Open).

기능적으로는 `localhost:3000`의 CORS 요청 허용과 기본적인 로우팅, 장애 격리 처리가 되어 있다. 특별할 것 없는 표준적인 구성이다.

---

## 2. 근본적인 의문: Gateway vs Nginx
문득 든 생각. "어차피 요청 받아서 뒤로 넘겨주는 건데, Nginx랑 뭐가 다른가?"
단순히 역할이 겹쳐 보이지만, 뜯어보면 태생부터 다르다.

### 차이점 정리

| 구분 | Spring Cloud Gateway (SCG) | Nginx |
| :--- | :--- | :--- |
| **기반** | JVM (Java/Kotlin) | C (Native) |
| **성능** | 무겁다. JVM 메모리 오버헤드가 있고 요청 처리가 상대적으로 느리다. | 압도적으로 빠르다. 적은 리소스로 수만 커넥션 처리 가능. |
| **제어 영역** | **애플리케이션 레벨**. ("이 유저 등급 확인해서 분기쳐라" 가능) | **인프라 레벨**. ("이미지는 여기서, API는 저기서" 처리) |
| **확장성** | Java 코드로 커스텀 로직 작성이 자유롭다. | 설정 파일 위주. 복잡한 로직을 넣으려면 Lua 스크립트를 써야 해서 제한적이다. |

### 결론
SCG는 **"똑똑하지만 느린 문지기"**고, Nginx는 **"단순하지만 빠른 배송기사"**다.
애초에 둘 중 하나를 고르는 문제가 아니다. 정적 파일 서빙이나 SSL 처리는 앞단에 Nginx를 둬서 처리하고, 상세한 비즈니스 로직(인증 등)이 필요한 라우팅은 뒷단의 SCG가 처리하는 구조가 정석이다. 
지금은 트래픽이 없으니 SCG 하나로 충분하겠지만, 나중에 부하가 심해지면 앞단에 Nginx를 붙여야 할 것이다.

---

# [Dev Log] Gateway 인증 필터 구현과 CORS 지옥 탈출기

**날짜**: 2026.01.22

오늘은 Spring Cloud Gateway에 본격적으로 **인증(Authorization)** 로직을 심고, 언제나 개발자를 괴롭히는 **CORS** 문제를 해결했다.

## 1. Global Filter 구현: `AuthorizationHeaderFilter`
Gateway는 모든 요청의 관문이다. 여기서 토큰 검사를 끝내야 백엔드가 비즈니스 로직에만 집중할 수 있다.
`AbstractGatewayFilterFactory`를 상속받아 JWT 검증 로직을 구현했다.

### 핵심 구현 사항
- **JWT 파싱 최적화**: 기존 코드의 비효율(검증 따로, 파싱 따로)을 개선해서 `parserBuilder`로 한 번만 파싱하도록 수정했다.
- **SSE 지원**: `EventSource`는 헤더 설정이 안 된다는 치명적인 단점이 있다. 그래서 쿼리 파라미터(`?token=...`)로 토큰이 들어와도 인증을 처리할 수 있도록 분기를 태웠다.
- **Fail Fast**: 유효하지 않은 토큰은 Gateway에서 즉시 `401 Unauthorized`를 뱉고 끝낸다. 

```kotlin
// AuthorizationHeaderFilter.kt (핵심 로직)
if (jwt.isBlank()) {
    return@GatewayFilter onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED)
}
// ... JWT 검증 로직 ...
```

## 2. 404 에러의 원인과 회로 차단기(Circuit Breaker)
개발 도중 황당한 `404 Not Found`를 만났다. 분명 경로는 맞는데 왜?

알고 보니 **Circuit Breaker**가 범인이었다. 인증 실패나 백엔드 다운 시 `resilience4j`가 `/fallback` 경로로 포워딩을 하는데, 정작 이 `/fallback`을 받아줄 컨트롤러를 안 만들었던 것.
Gatewawy 내부에 `FallbackController`를 만들어 `503 Service Unavailable`을 리턴하게 하니 문제가 명쾌해졌다.

```kotlin
@RestController
@RequestMapping("/fallback")
class FallbackController {
    @GetMapping
    fun fallback() = Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Backend is busy..."))
}
```

## 3. CORS: 환장의 헤더 중복 문제
프론트엔드와 연동하니 이번엔 CORS 에러가 터졌다.
**"The 'Access-Control-Allow-Origin' header contains multiple values"**

### 원인
Gateway와 백엔드(Spring Security)가 **"둘 다"** CORS 헤더를 붙여서 보내고 있었다.
1. 백엔드에서 `Allow-Origin: localhost:3000` 붙임
2. Gateway를 거치면서 또 `Allow-Origin: localhost:3000` 붙임
3. 브라우저: "헤더가 두 개? 보안 위반!" -> 차단

### 해결: `DedupeResponseHeader`
백엔드 코드를 건드리지 않고 Gateway 설정 한 줄로 해결했다. 중복된 헤더를 제거(Dedupe)해주는 필터다.

```yaml
default-filters:
  - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_UNIQUE
```

## 4. 로그인 경로 예외 처리
로그인(`POST /api/auth/login`)은 토큰이 없으므로 인증 필터를 타면 안 된다.
`application.yml`에서 라우트 순서를 조정해서 해결했다. 인증이 필요 없는 라우트(`auth-route`)를 인증 필터가 있는 라우트(`backend-route`)보다 **상위**에 배치하면, 먼저 매칭되어 필터를 건너뛴다.

오늘의 교훈: **"설정 파일의 들여쓰기(Indent) 하나가 6시간을 태울 수 있다."** (YAML Indent 실수로 라우팅이 통째로 날아갔던 건 비밀이다.)
