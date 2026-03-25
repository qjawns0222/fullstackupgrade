[Fullstack] networknt json-schema-validator로 API 계약 강제 검증 시스템 구축하기 — Bean Validation이 못 하는 것들

Bean Validation 쓰다 보면 한계가 있다. @NotBlank, @Size, @Pattern 같은 것들은 필드 수준에서는 잘 작동하는데, 진짜 복잡한 비즈니스 규칙을 표현하려 하면 금방 막힌다.

예를 들어 이런 케이스다. 취업 지원 상태가 INTERVIEW면 memo 필드가 반드시 있어야 한다. APPLIED면 없어도 된다. Bean Validation으로 이걸 구현하려면 커스텀 @Constraint 어노테이션을 만들고, ConstraintValidator를 구현하고, 메시지 리소스까지 챙겨야 한다. 비즈니스 규칙 하나에 클래스 두 개가 생긴다.

그리고 더 큰 문제가 있다. Bean Validation은 코드 레벨이다. 어떤 규칙이 어떤 엔드포인트에 적용되는지 문서화가 안 된다. OpenAPI 스펙에는 안 붙고, 클라이언트 팀은 에러 나봐야 안다.

JSON Schema는 이 문제를 다른 방식으로 푼다. 규칙을 코드가 아닌 json 파일에 선언한다. if/then/else, oneOf, allOf, additionalProperties 같은 키워드로 복잡한 조건을 표현할 수 있다. 그리고 스키마 파일 자체가 계약서다.

build.gradle에 없던 라이브러리다.

implementation 'com.networknt:json-schema-validator:1.3.3'

json-schema-validator 1.3.3이 이번에 추가한 라이브러리다. Draft-04부터 Draft-2020-12까지 지원하는데 여기선 Draft-7을 썼다. if/then 구문이 Draft-7에서 정식 지원된다.

스키마 파일 예시다. resources/schemas/job-application.json이다.

{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["companyName", "position", "status", "appliedDate"],
  "properties": {
    "companyName": { "type": "string", "minLength": 1, "maxLength": 100 },
    "position": { "type": "string", "minLength": 1, "maxLength": 100 },
    "status": {
      "type": "string",
      "enum": ["APPLIED", "INTERVIEW", "REJECTED", "PASSED", "OFFER_RECEIVED"]
    },
    "appliedDate": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$" },
    "memo": { "type": ["string", "null"], "maxLength": 1000 }
  },
  "additionalProperties": false,
  "if": {
    "properties": { "status": { "const": "INTERVIEW" } },
    "required": ["status"]
  },
  "then": {
    "properties": { "memo": { "type": "string", "minLength": 1 } },
    "required": ["memo"]
  }
}

additionalProperties: false가 포인트다. 정의되지 않은 필드가 요청에 들어오면 즉시 거부된다. API 계약 밖의 필드를 조용히 무시하는 게 아니라 에러를 낸다. 오타 필드명, 버전 간 필드 불일치 같은 것들을 잡아낸다.

if/then 블록은 이렇게 읽으면 된다. status가 INTERVIEW이면, memo는 반드시 있어야 하고 빈 문자열도 안 된다. 이걸 Bean Validation으로 표현하려면 글래스레벨 커스텀 어노테이션이 필요한데, 스키마 파일 몇 줄로 끝난다.

AOP 구현이다. @ValidateJsonSchema 어노테이션을 컨트롤러 메서드에 붙이면 AspectJ @Around가 가로채서 @RequestBody 인자를 추출하고 검증한다.

@Aspect
@Component
class JsonSchemaValidationAspect(
    private val schemaRegistry: SchemaRegistry,
    private val violationStore: ViolationStore,
    private val objectMapper: ObjectMapper
) {

    @Around("@annotation(validateJsonSchema)")
    fun validate(joinPoint: ProceedingJoinPoint, validateJsonSchema: ValidateJsonSchema): Any? {
        val schemaPath = validateJsonSchema.schemaPath
        val requestBody = resolveRequestBody(joinPoint)

        if (requestBody != null) {
            val schema = schemaRegistry.getSchema(schemaPath)
            val jsonNode = objectMapper.valueToTree<JsonNode>(requestBody)
            val errors = schema.validate(jsonNode)

            if (errors.isNotEmpty()) {
                val messages = errors.map { it.message }
                val violation = SchemaViolation(
                    id = UUID.randomUUID().toString(),
                    schemaPath = schemaPath,
                    endpoint = resolveRequestInfo().first,
                    method = resolveRequestInfo().second,
                    violations = messages,
                    requestPayload = objectMapper.writeValueAsString(requestBody)
                )
                violationStore.record(violation)
                throw SchemaValidationException(messages, schemaPath)
            }
        }

        return joinPoint.proceed()
    }
}

resolveRequestBody가 핵심이다. MethodSignature에서 파라미터 목록을 꺼내고, @RequestBody 어노테이션이 붙은 파라미터를 찾아서 해당 args 인덱스의 값을 반환한다. Spring이 이미 역직렬화한 객체다. 그걸 objectMapper.valueToTree로 JsonNode로 다시 변환해서 스키마에 넘긴다.

JsonSchemaRegistry는 스키마를 클래스패스에서 로드하고 ConcurrentHashMap으로 캐싱한다. 앱 시작 후 첫 요청에 한 번만 파일 I/O가 일어나고 이후는 메모리다.

class JsonSchemaRegistry : SchemaRegistry {
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    private val cache = ConcurrentHashMap<String, JsonSchema>()

    override fun getSchema(path: String): JsonSchema {
        return cache.getOrPut(path) {
            val resource = ClassPathResource(path)
            if (!resource.exists()) throw IllegalArgumentException("JSON Schema not found at classpath: $path")
            resource.inputStream.use { factory.getSchema(it) }
        }
    }
}

violations는 SchemaViolationStore에 기록된다. ConcurrentLinkedDeque로 thread-safe하게 관리하고 최대 500개까지 보관한다. 500개 초과되면 가장 오래된 것부터 evict된다. /api/schema-validation/violations로 조회하고, /api/schema-validation/stats로 스키마별, 엔드포인트별 집계를 볼 수 있다.

컨트롤러 적용은 어노테이션 하나다.

@PostMapping
@ValidateJsonSchema(schemaPath = "schemas/job-application.json")
fun createApplication(
    @RequestBody request: JobApplicationRequest,
    principal: Principal
): ResponseEntity<JobApplicationResponse>

검증 실패 시 GlobalExceptionHandler가 잡아서 400을 내려준다.

{
  "code": "SCHEMA_VALIDATION_FAILED",
  "message": "Request body does not conform to API schema",
  "schema": "schemas/job-application.json",
  "violations": [
    "$.companyName: is missing but it is required",
    "$.unknownField: is not defined in the schema and the schema does not allow additional properties"
  ]
}

violations 배열에 에러가 여러 개 한꺼번에 나오는 게 좋다. Bean Validation 기본 동작은 fail-fast라서 에러 하나 고치고 다시 요청해봐야 다음 에러가 나온다. json-schema-validator는 기본이 전체 검사라서 모든 에러를 한 번에 반환한다.

테스트다. 가장 까다로운 부분은 Mockito로 Kotlin 어노테이션 클래스를 mock할 수 없다는 점이다. @ValidateJsonSchema는 Kotlin annotation class인데, Mockito가 이걸 mock하면 Method.getParameterTypes()에서 NPE가 난다. 해결책은 reflection으로 실제 어노테이션 인스턴스를 가져오는 것이다.

object AnnotationHolder {
    @ValidateJsonSchema(schemaPath = "schemas/job-application.json")
    fun annotatedMethod() {}

    fun getAnnotation(): ValidateJsonSchema =
        this::class.java.getMethod("annotatedMethod").getAnnotation(ValidateJsonSchema::class.java)
}

이 패턴은 Kotlin에서 어노테이션을 테스트할 때 꽤 자주 쓰게 된다. 프록시 객체가 아닌 진짜 어노테이션 인스턴스를 쓰는 것이다.

프론트엔드는 /schema-validation 페이지다. 5초 폴링으로 최신 violations를 가져오고, 스키마별/엔드포인트별 분포 바 차트를 보여준다. payload 토글로 어떤 요청이 위반을 일으켰는지 확인할 수 있다.

이번 구현에서 얻은 게 명확하다. API 계약은 코드 밖에 있어야 한다. 스키마 파일 하나가 검증 로직, 문서, 테스트 픽스처 역할을 동시에 한다. 비즈니스 규칙이 바뀌면 json 파일만 수정하면 되고 컴파일도 필요 없다. 운영 환경에서 어떤 클라이언트가 어떤 규칙을 얼마나 자주 어기는지 violation store에서 바로 확인 가능하다. Bean Validation이 못 하는 조건부 검증을 if/then으로 선언적으로 표현할 수 있다.
