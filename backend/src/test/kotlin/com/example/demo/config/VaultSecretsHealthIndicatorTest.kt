package com.example.demo.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultSecretsHealthIndicatorTest {

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
            uri = "http://localhost:19999",
            kvBackend = "secret",
            applicationName = "aiblog"
        )
        val indicator = VaultSecretsHealthIndicator(status)

        val health = indicator.health()

        assertEquals("DOWN", health.status.code)
        assertTrue(health.details.containsKey("error") || health.details.containsKey("exception"))
    }

    @Test
    fun `vault status controller returns correct mode when disabled`() {
        val status = VaultConfigurationStatus(enabled = false)
        val indicator = VaultSecretsHealthIndicator(status)
        val controller = VaultSecretsController(status, indicator)

        val response = controller.status()

        assertFalse(response.enabled)
        assertEquals("local-fallback", response.mode)
        assertTrue(response.healthy)
    }

    @Test
    fun `secrets manifest lists all managed secrets`() {
        val status = VaultConfigurationStatus(enabled = true, uri = "http://localhost:8200")
        val indicator = VaultSecretsHealthIndicator(status)
        val controller = VaultSecretsController(status, indicator)

        val manifest = controller.secretsManifest()

        val keys = manifest.secrets.map { it.key }
        assertTrue(keys.contains("spring.datasource.password"))
        assertTrue(keys.contains("spring.mail.password"))
        assertTrue(keys.contains("jwt.secret"))
        assertTrue(manifest.secrets.all { it.managedByVault })
    }

    @Test
    fun `secrets manifest marks secrets as not managed when vault disabled`() {
        val status = VaultConfigurationStatus(enabled = false)
        val indicator = VaultSecretsHealthIndicator(status)
        val controller = VaultSecretsController(status, indicator)

        val manifest = controller.secretsManifest()

        assertTrue(manifest.secrets.none { it.managedByVault })
    }
}
