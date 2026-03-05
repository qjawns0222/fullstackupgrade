[Fullstack] Unleash를 이용한 동적 기능 활성화(Feature Toggle) 적용기

최근 프로젝트가 커지면서 리소스를 많이 잡아먹는 특정 서비스들(예: AI 기반 OCR 분석)에 대한 제어권이 필요하다는 생각이 들었습니다. 배포를 새로 하지 않고도 런타임에 특정 기능을 죽이거나 살릴 수 있는 구조, 즉 'Feature Toggle'을 도입한 과정을 정리해봅니다.

이번에 선택한 도구는 Unleash입니다. 단순한 if-else 처리가 아니라, 아키텍처 관점에서 어떻게 깔끔하게 녹여낼 수 있을지 고민하며 작업했습니다.

핵심 설계: AOP를 활용한 선언적 제어
비즈니스 로직 곳곳에 'if(featureEnabled)' 같은 코드를 집어넣는 것은 시니어 개발자답지 않습니다. 저는 AOP를 활용하여 어노테이션 하나로 기능을 제어할 수 있는 구조를 잡았습니다.

1. FeatureToggle 어노테이션 정의
package com.example.demo.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureToggle(val name: String)

2. Aspect를 이용한 공통 로직 처리
매번 Unleash 클라이언트를 수동으로 호출하지 않고, Aspect에서 기능을 체크하도록 구현했습니다.

@Aspect
@Component
class FeatureToggleAspect(private val unleash: Unleash) {
    @Around("@annotation(featureToggle)")
    fun checkFeature(joinPoint: ProceedingJoinPoint, featureToggle: FeatureToggle): Any? {
        if (!unleash.isEnabled(featureToggle.name)) {
            throw FeatureDisabledException(featureToggle.name)
        }
        return joinPoint.proceed()
    }
}

기능 적용: AI 분석 업로드 차단
이제 컨트롤러에서는 복잡한 로직 없이 어노테이션만 붙여주면 됩니다.

@PostMapping
@FeatureToggle(name = "ai-analysis")
fun uploadFile(...) { ... }

인프라적 결함 해결과 개인적인 의견
기존에는 예상치 못한 트래픽이나 외부 API 장애 시 시스템 전체 부하를 막기 위해 수동으로 설정을 바꾸고 배포해야 했습니다. 하지만 이번 Feature Toggle 도입으로 운영 환경에서의 대응력이 한 단계 격상되었습니다.

특히 단순히 Boolean 체크를 넘어, Unleash가 제공하는 Gradual Rollout(단계적 배포)이나 UserID 기반 타겟팅을 활용할 수 있는 토대를 마련했다는 점에서 의미가 큽니다.

프론트엔드에서도 관리자 페이지를 통해 현재 기능들의 활성 상태를 모니터링할 수 있는 UI를 추가했습니다. 백엔드의 견고함과 프론트엔드의 가시성이 만났을 때 진정한 운영 품질이 확보된다고 생각합니다.

테스트 코드 역시 Mockito를 활용해 Aspect의 동작을 100% 검증했습니다. 인프라성 기능일수록 테스트는 타협의 대상이 아닙니다.
