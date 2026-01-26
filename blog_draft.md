# [Fullstack] 입사지원 관리 시스템(ATS) 백엔드 구현

## 1. 개발 배경 (Why)
취업 준비를 하다 보면 수많은 기업에 지원하게 된다. 엑셀이나 노션으로 관리할 수도 있지만, 개발자로서 내 커리어를 관리하는 '커리어 프로젝트' 내에서 직접 입사지원 현황을 관리하고 싶었다. 그래서 오늘은 '입사지원 관리 시스템(ATS)'의 백엔드 기능을 구현하기로 결심했다.

## 2. 설계 및 구현 (Design & Implementation)
백엔드는 Kotlin과 Spring Boot를 기반으로 하며, 견고한 계층형 아키텍처를 따랐다.

### 2.1 도메인 설계 (Entity)
가장 먼저 `JobApplication` 엔티티를 정의했다. 회사명(`companyName`), 포지션(`position`), 지원 상태(`status`), 지원 날짜(`appliedDate`), 메모(`memo`) 등을 포함하도록 설계했다. 특히 `status`는 `Enum`으로 정의하여 `APPLIED`, `INTERVIEW`, `PASSED` 등으로 상태를 명확히 관리하도록 했다.

### 2.2 비즈니스 로직 (Service)
`JobApplicationService`에서는 CRUD 로직을 충실히 구현했다. 보안을 위해 모든 데이터 접근 시 현재 로그인한 사용자의 ID(`userId`)와 데이터 소유자의 ID가 일치하는지 검증하는 로직을 추가했다. 남의 지원 내역을 보거나 수정하면 안 되기 때문이다.

### 2.3 API 엔드포인트 (Controller)
`JobApplicationController`에서는 RESTful API를 제공한다. `Principal` 객체를 통해 인증된 사용자의 정보를 가져오고, 이를 서비스 계층으로 전달한다.

```kotlin
@PostMapping
fun createApplication(@RequestBody request: JobApplicationRequest, principal: Principal): ResponseEntity<JobApplicationResponse> {
    val userId = getUserId(principal)
    val response = jobApplicationService.createApplication(userId, request)
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
}
```

## 3. 테스트 및 검증 (Testing)
기능 구현만큼 중요한 것이 테스트다. `JobApplicationServiceTest`를 작성하여 Mockito를 이용한 단위 테스트를 수행했다. 외부 의존성(Repository)을 Mocking하여 순수 비즈니스 로직이 의도대로 동작하는지 검증했고, `./gradlew test`를 통해 모든 테스트가 통과함을 확인했다.

## 4. 마치며 (Retrospective)
단순한 CRUD 같지만, 사용자별 데이터 격리(Isolation)와 테스트 코드 작성이라는 기본 원칙을 지키며 개발했다. 다음에는 이 API를 연동하여 프론트엔드에서 시각적으로 지원 현황을 볼 수 있는 대시보드를 만들어야겠다. 오늘도 미션 클리어!
