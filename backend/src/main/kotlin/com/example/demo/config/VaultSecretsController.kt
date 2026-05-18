package com.example.demo.config

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/vault")
class VaultSecretsController(
    private val vaultConfigurationStatus: VaultConfigurationStatus,
    private val vaultSecretsHealthIndicator: VaultSecretsHealthIndicator
) {

    @GetMapping("/status")
    fun status(): VaultStatusResponse {
        val health = vaultSecretsHealthIndicator.health()
        return VaultStatusResponse(
            enabled = vaultConfigurationStatus.enabled,
            mode = if (vaultConfigurationStatus.enabled) "vault" else "local-fallback",
            uri = vaultConfigurationStatus.uri.ifBlank { null },
            kvBackend = vaultConfigurationStatus.kvBackend,
            applicationName = vaultConfigurationStatus.applicationName,
            healthy = health.status.code == "UP",
            details = health.details.mapValues { it.value.toString() }
        )
    }

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
}

data class VaultStatusResponse(
    val enabled: Boolean,
    val mode: String,
    val uri: String?,
    val kvBackend: String,
    val applicationName: String,
    val healthy: Boolean,
    val details: Map<String, String>
)

data class SecretsManifestResponse(val secrets: List<SecretEntry>)

data class SecretEntry(
    val key: String,
    val description: String,
    val managedByVault: Boolean
)
