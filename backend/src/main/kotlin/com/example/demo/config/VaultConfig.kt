package com.example.demo.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

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

    private val log = LoggerFactory.getLogger(javaClass)

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

data class VaultConfigurationStatus(
    val enabled: Boolean,
    val uri: String = "",
    val kvBackend: String = "secret",
    val applicationName: String = "aiblog"
)
