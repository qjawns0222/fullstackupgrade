TITLE: [Fullstack] Spring Modulith를 활용한 도메인 기반 아키텍처 정립과 Transactional Outbox 패턴 구현

안녕하세요! 오늘은 프로젝트의 아키텍처를 견고하게 다듬고, 분산 시스템에서 흔히 발생하는 'Dual Write' 문제(이벤트 유실 문제)를 해결하기 위해 **Spring Modulith**를 도입한 경험을 공유하고자 합니다.

## 1. 문제 상황: Dual Write와 이벤트 유실
우리 시스템에서는 AI 분석 요청이나 감사 로그(Audit Log) 처리를 비동기 이벤트 방식으로 처리하고 있었습니다. 하지만 일반적인 `@EventListener`와 외부 메시지 브로커(RabbitMQ, Redis 등)를 사용할 경우, **DB 트랜잭션은 성공했지만 메시지 발행에 실패**하거나, 반대로 **메시지는 발행됐지만 DB 롤백이 발생하는** 'Dual Write' 문제가 발생할 수 있습니다.

이러한 문제를 방지하기 위해 **Transactional Outbox 패턴**이 필요했고, 이를 가장 효율적으로 구현할 수 있는 **Spring Modulith**를 선택했습니다.

## 2. Spring Modulith 도입

### 의존성 추가
Spring Modulith는 아키텍처 검증뿐만 아니라 이벤트 발행 레지스트리 기능을 제공합니다.

```gradle
// build.gradle
dependencies {
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    implementation 'org.springframework.modulith:spring-modulith-starter-jpa'
    implementation 'org.springframework.modulith:spring-modulith-events-jpa'
    testImplementation 'org.springframework.modulith:spring-modulith-test'
    testImplementation 'org.springframework.modulith:spring-modulith-docs'
}
```

### 도메인 지향 구조로의 리팩토링 (Vertical Slices)
기존의 기술 계층 중심(`controller`, `service`, `repository`) 구조는 모듈 간 경계가 모호하고 순환 참조가 발생하기 쉽습니다. 이를 도메인 중심의 수직 슬라이스로 재편성했습니다.

- `com.example.demo.analysis`: AI 분석 도메인 (Controller, Service, Repository 포함)
- `com.example.demo.audit`: 감사 로그 도메인
- `com.example.demo.shared`: 공통 인프라 (S3Service 등)

### Transactional Outbox 구현
`@ApplicationModuleListener`를 사용하면 Spring Modulith가 내부적으로 이벤트를 DB(이벤트 출구함)에 기록하고, 트랜잭션이 성공한 경우에만 리스너를 실행하도록 보장합니다.

```kotlin
// AiAnalysisEventListener.kt
@Component
class AiAnalysisEventListener(...) {

    @ApplicationModuleListener // Modulith의 마법: 트랜잭션 보장 및 자동 재시도
    fun handleAiAnalysis(event: AiAnalysisEvent) {
        // AI 분석 로직...
    }
}
```

## 3. 아키텍처 검증 루프
Spring Modulith의 가장 큰 장점 중 하나는 코드로 아키텍처를 검증할 수 있다는 점입니다.

```kotlin
// ModularityTest.kt
class ModularityTest {
    @Test
    fun verifyModularity() {
        val modules = ApplicationModules.of(DemoApplication::class.java)
        modules.verify() // 모듈 간 위반 사항 및 순환 참조 체크
        
        // 문서 자동 생성 (AsciiDoc, Mermaid 등)
        Documenter(modules).writeModulesAsPlantUml()
    }
}
```

실제로 검증 과정에서 `analysis` 도메인이 `service` 레이어를 참조하고, 다시 `service` 레이어의 클래스가 `analysis` 도메인의 엔티티를 참조하는 순환 구조를 발견하여, 핵심 로직을 해당 도메인 내부로 응집시키는 리팩토링을 수행했습니다.

## 4. 마치며
Spring Modulith를 통해 다음과 같은 성과를 얻었습니다.

1. **데이터 일관성 보장**: Transactional Outbox 패턴을 통해 장애 상황에서도 이벤트 유실 없는 시스템을 구축했습니다.
2. **아키텍처 강제화**: 테스트 코드를 통해 기술 계층 간의 무분별한 참조를 막고 도메인 간의 결합도를 낮췄습니다.
3. **가시성 확보**: 모듈 구조를 자동으로 문서화하여 시스템의 복잡도를 한눈에 파악할 수 있게 되었습니다.

진정한 마이크로서비스로 가기 전, '모듈형 모놀리스(Modulith)'는 개발 생산성과 아키텍처 건전성을 동시에 잡을 수 있는 훌륭한 전략임을 다시 한번 확인했습니다.
