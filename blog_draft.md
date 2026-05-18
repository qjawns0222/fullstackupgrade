[Fullstack] HashiCorp Vault 시크릿 관리 - application.yml 평문 비밀번호를 없애다

---

application.yml 파일을 열면 DB 비밀번호, Gmail 앱 비밀번호, JWT 시크릿이 그대로 적혀 있다. 로컬 개발 환경이라 넘어가고 있었지만, 코드베이스를 들여다볼 때마다 찜찜했다. Git 이력에 박혀있는 평문 시크릿은 한 번 올라가면 지울 수 없다. 언젠가 레포지토리를 공개하거나 팀원이 생기는 순간 문제가 된다.

HashiCorp Vault를 붙이기로 했다. Spring Cloud Vault가 있으니 설정만 잘 맞추면 될 거라고 생각했는데, 생각보다 신경 써야 할 부분이 있었다.

---

가장 먼저 부딪힌 문제는 `spring.config.import=optional:vault://` 설정이었다.

Spring Cloud Vault 4.x는 bootstrap.yml 방식 대신 `spring.config.import`를 쓴다. 공식 문서대로 `application.yml`에 추가했더니 전체 테스트가 무더기로 실패했다. 단독으로 돌리면 통과하는 테스트들이 전체 실행에서는 죄다 깨진다.

원인은 Spring Boot 3.2의 컨텍스트 로딩 순서 문제였다. `spring-cloud-vault-config`가 클래스패스에 있으면 `ConfigDataEnvironmentPostProcessor`가 Vault 연결을 시도하고, 테스트 컨텍스트 초기화 단계에서 연결 실패가 전파된다. `optional:` 접두사가 있어도 환경에 따라 컨텍스트 자체가 오염된다.

해결 방법은 간단했다. `application.yml`에 `spring.config.import`를 넣지 않고, `application-vault.yml` 프로파일에만 Vault 설정을 격리하는 것이다. 로컬에서는 `vault.enabled=false`가 기본값이고, 프로덕션에서는 `--spring.profiles.active=vault`로 활성화한다.

```yaml
# application.yml (기본)
vault:
  enabled: false

# application-vault.yml (프로파일 활성화 시에만 로딩)
spring:
  cloud:
    vault:
      uri: ${VAULT_URI:http://localhost:8200}
      authentication: TOKEN
      token: ${VAULT_TOKEN:}
      kv:
        enabled: true
        backend: secret
        application-name: aiblog
      fail-fast: false

vault:
  enabled: true
  uri: ${VAULT_URI:http://localhost:8200}
```

`fail-fast: false`가 중요하다. Vault에 연결이 안 되더라도 애플리케이션이 시작되게 한다. 연결 실패 시 헬스체크에서 DOWN으로 표시되면 충분하고, 앱 자체가 죽으면 곤란하다.

---

핵심 구조는 세 파일이다.

`VaultConfig.kt`는 `@ConfigurationProperties`로 `vault.*` 설정을 읽어 `VaultConfigurationStatus` 빈을 만든다. 이 빈이 Vault 활성화 여부와 연결 정보를 담는다.

```kotlin
@ConfigurationProperties(prefix = "vault")
data class VaultProperties(
    val enabled: Boolean = false,
    val uri: String = "http://localhost:8200",
    val token: String = "",
    val kv: KvProperties = KvProperties()
) {
    data class KvProperties(
        val backend: String = "secret",
        val applicationName: String = "aiblog"
    )
}

@Configuration
@EnableConfigurationProperties(VaultProperties::class)
class VaultConfig(private val vaultProperties: VaultProperties) {

    @Bean
    fun vaultConfigurationStatus(): VaultConfigurationStatus {
        return if (vaultProperties.enabled) {
            log.info("Vault integration enabled — uri={}", vaultProperties.uri)
            VaultConfigurationStatus(
                enabled = true,
                uri = vaultProperties.uri,
                kvBackend = vaultProperties.kv.backend,
                applicationName = vaultProperties.kv.applicationName
            )
        } else {
            log.warn("Vault integration disabled — secrets loaded from application.yml (dev mode)")
            VaultConfigurationStatus(enabled = false)
        }
    }
}
```

`VaultSecretsHealthIndicator.kt`는 Actuator `HealthIndicator`를 구현한다. Vault가 비활성화면 `UP`에 `local-fallback` 모드를 달아 반환하고, 활성화면 Vault `/v1/sys/health`를 직접 호출해서 initialized/sealed 상태를 확인한다.

```kotlin
@Component
class VaultSecretsHealthIndicator(
    private val vaultConfigurationStatus: VaultConfigurationStatus
) : HealthIndicator {

    override fun health(): Health {
        if (!vaultConfigurationStatus.enabled) {
            return Health.up()
                .withDetail("mode", "local-fallback")
                .withDetail("message", "Vault disabled — using application.yml secrets")
                .build()
        }

        return runCatching {
            val rt = RestTemplate()
            val sysHealth = rt.getForObject(
                "${vaultConfigurationStatus.uri}/v1/sys/health", Map::class.java
            )
            val initialized = sysHealth?.get("initialized") as? Boolean ?: false
            val sealed = sysHealth?.get("sealed") as? Boolean ?: true

            if (initialized && !sealed) {
                Health.up()
                    .withDetail("mode", "vault")
                    .withDetail("initialized", initialized)
                    .withDetail("sealed", sealed)
                    .build()
            } else {
                Health.down()
                    .withDetail("initialized", initialized)
                    .withDetail("sealed", sealed)
                    .build()
            }
        }.getOrElse { ex ->
            Health.down()
                .withDetail("error", ex.message ?: "connection failed")
                .withException(ex)
                .build()
        }
    }
}
```

`runCatching`으로 연결 실패를 조용히 처리한다. Vault 서버가 없는 로컬 개발환경에서 헬스체크 때문에 로그가 빨개지는 걸 원하지 않는다.

`VaultSecretsController.kt`는 `/api/vault/status`와 `/api/vault/secrets/manifest` 두 엔드포인트를 제공한다. status는 현재 연결 상태를, manifest는 어떤 시크릿이 Vault에서 관리되는지 목록을 반환한다.

```kotlin
@GetMapping("/secrets/manifest")
fun secretsManifest(): SecretsManifestResponse {
    return SecretsManifestResponse(
        secrets = listOf(
            SecretEntry("spring.datasource.password", "DB 비밀번호", vaultConfigurationStatus.enabled),
            SecretEntry("spring.mail.password", "Gmail 앱 비밀번호", vaultConfigurationStatus.enabled),
            SecretEntry("jwt.secret", "JWT 서명 키", vaultConfigurationStatus.enabled),
            SecretEntry("aws.s3.access-key", "MinIO 액세스 키", vaultConfigurationStatus.enabled),
            SecretEntry("aws.s3.secret-key", "MinIO 시크릿 키", vaultConfigurationStatus.enabled)
        )
    )
}
```

`managedByVault` 플래그가 false면 프론트엔드에서 "application.yml" 배지를 표시한다. Vault가 켜지면 자동으로 "Vault" 배지로 바뀐다.

---

테스트는 인프라 의존성 없이 순수 단위 테스트로 작성했다.

```kotlin
@Test
fun `vault disabled returns UP with local-fallback mode`() {
    val status = VaultConfigurationStatus(enabled = false)
    val indicator = VaultSecretsHealthIndicator(status)

    val health = indicator.health()

    assertEquals("UP", health.status.code)
    assertEquals("local-fallback", health.details["mode"])
}

@Test
fun `vault enabled but unreachable returns DOWN`() {
    val status = VaultConfigurationStatus(
        enabled = true,
        uri = "http://localhost:19999"  // 없는 포트
    )
    val indicator = VaultSecretsHealthIndicator(status)

    val health = indicator.health()

    assertEquals("DOWN", health.status.code)
}
```

Vault 서버 없이 비활성 모드, 연결 불가 모드, manifest 목록을 모두 검증한다. `@SpringBootTest` 없이 도는 테스트라 빠르다.

---

프론트엔드는 `/admin/vault` 페이지로 만들었다. 현재 모드(local-fallback / vault), 연결 상태, 시크릿 목록을 5초 간격으로 폴링한다. Vault가 비활성화 상태면 노란 박스로 프로파일 활성화 방법을 안내한다.

```
--spring.profiles.active=vault
VAULT_URI=http://your-vault:8200
VAULT_TOKEN=your-token
```

이 세 가지만 설정하면 `application-vault.yml`이 로딩되면서 Spring Cloud Vault가 KV 엔진에서 시크릿을 가져온다.

---

이번 구현의 핵심은 로컬 개발을 깨뜨리지 않으면서 프로덕션에서는 Vault를 쓸 수 있는 구조를 만드는 것이었다. `optional:vault://` 한 줄로 해결하려다가 테스트 전체가 깨지는 걸 경험하고 나서야 프로파일 분리가 올바른 방향임을 확신했다.

application.yml에 아직 평문으로 남아있는 시크릿들은 Vault 프로파일을 활성화하면 KV 엔진의 `secret/aiblog` 경로에서 덮어쓰기된다. Git 이력에 남아있는 건 어쩔 수 없지만, 적어도 앞으로는 새 시크릿이 코드베이스에 들어가지 않는다.
