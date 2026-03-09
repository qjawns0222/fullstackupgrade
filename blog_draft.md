[Fullstack] Bucket4j와 Redis를 이용한 분산 환경 Rate Limiting 구현하기

대규모 트래픽이 몰리는 서비스에서 특정 사용자나 IP의 무분별한 요청을 막는 것은 보안과 가용성 측면에서 필수적입니다. 특히 AI 분석과 같이 리소스를 많이 소모하는 API는 더욱 철저한 제어가 필요하죠.

이번 미션에서는 Bucket4j와 Redis를 결합하여, 여러 서버 인스턴스에서도 동일하게 적용되는 '분산 처리율 제한(Distributed Rate Limiting)' 기능을 도입했습니다.

핵심 설계: 분산 환경에서의 상태 공유
로컬 메모리를 사용하는 처리율 제한은 서버가 여러 대일 경우 한계가 있습니다. 저는 이를 해결하기 위해 Redis를 백엔드 저장소로 활용했습니다.

1. @RateLimit 어노테이션 정의
package com.example.demo.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimit(
    val key: String = "",
    val capacity: Long = 10,
    val tokens: Long = 10,
    val seconds: Long = 60
)

2. Interceptor를 통한 공통 제어
Spring MVC Interceptor를 사용하여 컨트롤러에 도달하기 전 토큰을 검증하도록 구현했습니다.

@Component
class RateLimitInterceptor(private val proxyManager: ProxyManager<ByteArray>) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // ... 생략 ...
        val bucket: Bucket = proxyManager.builder().build(key.toByteArray(), configuration)
        if (bucket.tryConsume(1)) {
            return true
        } else {
            throw RateLimitExceededException("Too many requests.")
        }
    }
}

실전 적용: 로그인 및 AI 분석 엔드포인트
무차별 대입 공격(Brute Force)의 위험이 있는 로그인 API와 고비용의 AI 분석 업로드 API에 각각 최적화된 Rate Limit을 적용했습니다.

- 로그인: 1분당 5회 제한
- AI 분석: 1시간당 10회 제한

프론트엔드 사용자 경험(UX) 개선
단순히 요청을 차단하는 데 그치지 않고, 프론트엔드(`AnalysisPage`)에서 429(Too Many Requests) 에러 발생 시 사용자에게 친절한 안내 메시지를 출력하도록 개선했습니다.

자가 치유(Self-Healing) 과정
구현 과정 중 Kotlin의 메서드 오버로딩 해결(Bucket4j의 build 메서드) 과정에서 컴파일 에러가 발생했으나, 런타임 분석과 명시적 타입 캐스팅을 통해 해결했습니다. 인프라성 기능일수록 환경에 따른 변수를 잡는 것이 중요하다는 것을 다시 한번 깨달았습니다.

이번 구현을 통해 서비스의 안정성을 한 단계 높였으며, 앞으로도 더 견고한 아키텍처를 위해 고민하겠습니다.
