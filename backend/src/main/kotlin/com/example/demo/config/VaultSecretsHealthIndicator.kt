package com.example.demo.config

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

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
            val sysHealth = rt.getForObject("${vaultConfigurationStatus.uri}/v1/sys/health", Map::class.java)
            val initialized = sysHealth?.get("initialized") as? Boolean ?: false
            val sealed = sysHealth?.get("sealed") as? Boolean ?: true

            if (initialized && !sealed) {
                Health.up()
                    .withDetail("mode", "vault")
                    .withDetail("uri", vaultConfigurationStatus.uri)
                    .withDetail("backend", vaultConfigurationStatus.kvBackend)
                    .withDetail("initialized", initialized)
                    .withDetail("sealed", sealed)
                    .build()
            } else {
                Health.down()
                    .withDetail("mode", "vault")
                    .withDetail("uri", vaultConfigurationStatus.uri)
                    .withDetail("initialized", initialized)
                    .withDetail("sealed", sealed)
                    .build()
            }
        }.getOrElse { ex ->
            Health.down()
                .withDetail("mode", "vault")
                .withDetail("uri", vaultConfigurationStatus.uri)
                .withDetail("error", ex.message ?: "connection failed")
                .withException(ex)
                .build()
        }
    }
}
