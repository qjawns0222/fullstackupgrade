[Fullstack] AI 기반 이력서 스코어링 - Spring AI + GPT-4o-mini로 직무 적합도 0-100 자동 산출하기

---

OCR로 이력서 텍스트를 뽑아내는 기능은 진작에 만들어뒀다. 그런데 텍스트가 추출되고 나면 그냥 DB에 저장만 된다. 정작 "이 사람이 백엔드 포지션에 얼마나 맞는가"는 HR 담당자가 직접 읽어야 판단할 수 있는 구조였다. OCR 결과를 LLM에 던져서 구조화된 점수를 받아오는 게 자연스러운 다음 단계라는 생각이 들었다.

Spring AI를 쓰기로 했다. LangChain4j도 고려했지만, Spring 생태계 안에서 ChatClient 빈 하나로 해결되는 Spring AI가 유지보수 측면에서 훨씬 낫다. OpenAI 클라이언트를 직접 wrapping하면 retry, timeout, 구조화 출력 변환을 전부 손으로 짜야 하는데 Spring AI는 이걸 다 해준다.


의존성을 추가하다가 첫 번째 함정을 만났다. `features-todo.md`에는 `spring-ai-openai-spring-boot-starter:1.0.0`이라고 적혀 있었는데, Maven Central에 이 버전이 없다. 실제 최신 버전은 1.0.0-M6이고 Spring milestone 리포지토리에서만 받을 수 있다.

```gradle
repositories {
    mavenCentral()
    maven { url 'https://repo.spring.io/milestone' }
}

dependencies {
    implementation 'org.springframework.ai:spring-ai-openai-spring-boot-starter:1.0.0-M6'
}
```

그리고 두 번째 함정. `application.yml`에 Spring AI 설정을 추가하면서 파일 최하단에 `spring:` 블록을 새로 만들었다. 이미 파일 최상단에 `spring:` 블록이 있는데 중복 키가 생겼고, 전체 테스트가 `DuplicateKeyException`으로 터졌다. 기존 `spring:` 블록 안의 `graphql:` 아래에 `ai:` 섹션을 추가하는 방식으로 수정했다.

```yaml
spring:
  graphql:
    # ... 기존 설정
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:dummy-key-for-local}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.2
```


설계에서 가장 신경 쓴 부분은 ChatClient를 서비스에서 직접 의존하지 않게 하는 것이었다. 테스트에서 LLM 호출을 막을 수 없으면 단위 테스트가 실제 API를 때리게 된다. `LlmScoringClient` 포트 인터페이스를 만들고, 실제 Spring AI 호출은 `SpringAiScoringClient` 어댑터에 격리했다.

```kotlin
interface LlmScoringClient {
    fun requestScoring(resumeText: String, jobTitle: String): ResumeScoringResult
}

@Component
class SpringAiScoringClient(private val chatClient: ChatClient) : LlmScoringClient {
    private val converter = BeanOutputConverter(ResumeScoringResult::class.java)

    override fun requestScoring(resumeText: String, jobTitle: String): ResumeScoringResult {
        val format = converter.format
        val prompt = """
            You are an expert HR recruiter. Analyze the resume for: "$jobTitle".
            Resume Text: ...
            Respond ONLY with JSON matching: $format
        """.trimIndent()

        val response = chatClient.prompt(prompt).call().content()
            ?: throw IllegalStateException("LLM returned null response")

        return runCatching { converter.convert(response) }
            .getOrElse {
                log.warn("Failed to parse LLM response, using defaults")
                ResumeScoringResult()
            }
    }
}
```

`BeanOutputConverter`가 핵심이다. 데이터 클래스를 넘기면 JSON 스키마를 자동으로 생성해서 프롬프트에 format으로 주입할 수 있고, LLM 응답을 다시 객체로 역직렬화해준다. LLM이 가끔 스키마를 무시하고 이상한 JSON을 반환할 수 있으므로 `runCatching`으로 감싸고 파싱 실패 시 기본값을 반환하도록 했다.

```kotlin
data class ResumeScoringResult(
    val totalScore: Int = 0,
    val skillScore: Int = 0,
    val experienceScore: Int = 0,
    val educationScore: Int = 0,
    val extractedSkills: String = "",
    val extractedExperience: String = "",
    val extractedEducation: String = "",
    val summary: String = ""
)
```

점수 범위 보장도 중요하다. LLM이 가끔 150이나 -5 같은 값을 반환하는 경우가 있어서 저장 전에 반드시 clamp 처리를 한다.

```kotlin
val score = ResumeScore(
    totalScore = result.totalScore.coerceIn(0, 100),
    skillScore = result.skillScore.coerceIn(0, 100),
    // ...
)
```

DB 설계는 `analysis_requests` 테이블을 FK로 참조하는 별도 `resume_scores` 테이블로 분리했다. 하나의 이력서를 여러 직무로 평가할 수 있도록 1:N 구조다.

```sql
CREATE TABLE resume_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    analysis_request_id BIGINT NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    total_score INT NOT NULL,
    skill_score INT NOT NULL,
    experience_score INT NOT NULL,
    education_score INT NOT NULL,
    extracted_skills TEXT,
    extracted_experience TEXT,
    extracted_education TEXT,
    summary TEXT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_resume_scores_analysis FOREIGN KEY (analysis_request_id) REFERENCES analysis_requests(id)
);
```


테스트는 `FakeLlmScoringClient`와 `FakeResumeScoreStore`로 LLM과 DB를 완전히 격리했다. 이 프로젝트에서 Mockito `ArgumentCaptor`를 Kotlin non-null 타입과 함께 쓰면 NPE가 발생하는 경험을 이미 여러 번 했다. Fake 구현이 훨씬 안전하고 테스트 코드도 훨씬 읽기 쉽다.

```kotlin
class FakeLlmScoringClient : LlmScoringClient {
    var nextResult: ResumeScoringResult = ResumeScoringResult()
    override fun requestScoring(resumeText: String, jobTitle: String) = nextResult
}

@Test
fun `score - clamps totalScore over 100 to 100`() {
    llmClient.nextResult = ResumeScoringResult(totalScore = 150, skillScore = 100, ...)
    val result = service.score(2L, "resume", "Engineer")
    assertEquals(100, result.totalScore)
}
```

프론트엔드는 `/admin/resume-scoring` 페이지를 만들었다. 직무명과 이력서 텍스트를 입력하면 POST 요청을 보내고, 결과 카드를 5초마다 polling해서 갱신한다. 점수 시각화는 CSS 너비로 구현한 바 차트로 했다. 80점 이상은 초록, 60-80은 노랑, 60 미만은 빨강으로 색을 구분한다.

실제로 써보면 GPT-4o-mini가 꽤 합리적인 점수를 낸다. "Kotlin, Spring Boot 5년"이라고만 써도 백엔드 포지션에서 85점 정도가 나온다. 직무명을 바꾸면 같은 이력서라도 다른 점수가 나오는 게 LLM이 컨텍스트를 제대로 이해하고 있다는 증거다.

한 가지 남은 과제는 실제 OCR 결과와의 연동이다. 지금은 텍스트를 직접 입력해야 하는데, `AnalysisRequest` 완료 이벤트를 구독해서 자동으로 스코어링 파이프라인이 돌아가게 하면 더 자연스러운 흐름이 될 것이다.
