# [Fullstack] Spring Boot + Next.js 프로젝트에 Swagger UI (SpringDoc) 도입하기 + 설날 근황 [Revised 3]

안녕하세요! 4년차 백엔드 개발자입니다.
오늘은 사이드 프로젝트인 '여러 기능 삽입 테스트 프로젝트'에 **API 문서 자동화**를 도입한 과정을 정리해보려고 합니다.

혼자 개발하더라도 프론트엔드와 백엔드를 오가다 보면 "아, 이 API 파라미터가 뭐였지?" 하고 헷갈릴 때가 많습니다. 매번 코드를 열어보기도 귀찮고요. 그래서 **SpringDoc OpenAPI**를 적용하여 Swagger UI를 띄워보기로 했습니다.

## 1. 문제 상황 (Why?)
- API 명세서가 없어서 프론트엔드 개발 시 백엔드 코드를 뒤져봐야 함.
- Postman 컬렉션을 관리하는 것도 귀찮음.
- 코드와 문서의 싱크가 안 맞을 위험이 있음.

## 2. 해결 방안 (Solution)
- **SpringDoc OpenAPI v3** 라이브러리를 사용.
- 어노테이션 기반으로 문서를 자동 생성.
- Next.js 프론트엔드 헤더에 'API Docs' 링크 추가.

## 3. 적용 과정

### 3-1. 의존성 추가 (build.gradle)
먼저 `build.gradle`에 의존성을 추가합니다. Spring Boot 3.2.0을 사용 중이라 호환되는 버전을 선택했습니다.

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
```

### 3-2. Config 설정 (OpenApiConfig.kt)
기본적인 문서 타이틀과 버전을 설정합니다.

```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("AI Blog API")
                .description("API documentation for AI Blog service")
                .version("v1.0"))
    }
}
```

### 3-3. 프론트엔드 연동 (Header.tsx)
개발 편의성을 위해 헤더에 바로 접근할 수 있는 링크를 달아두었습니다.

```tsx
<Link href="http://localhost:8080/swagger-ui/index.html" target="_blank">
    API Docs
</Link>
```

## 4. 마치며 & 근황 (Lunar New Year Update) 🙇‍♂️

이렇게 해서 아주 간단하게 API 문서를 확보했습니다. 이제 프론트엔드 개발이 한결 수월해지겠네요.

**그리고... 사실 이번 포스팅이 좀 많이 늦어졌습니다.** 😅
핑계라면 핑계지만, **설날 연휴 동안 푹 쉬고 오느라 블로그 작성을 도통 못 했습니다.** (맛있는 것도 많이 먹고 리프레시 제대로 했습니다!)
연휴 후유증을 털어내고 다시 열심히 기능을 추가해볼 생각입니다. 다들 늦었지만 새해 복 많이 받으세요! 🙇‍♂️

다음에는 이 Swagger UI에 인증(JWT) 토큰을 끼얹어서 테스트하는 방법을 정리해보겠습니다.

---
*Tags: SpringBoot, Kotlin, Swagger, OpenAPI, Next.js, Fullstack, DevLog*
