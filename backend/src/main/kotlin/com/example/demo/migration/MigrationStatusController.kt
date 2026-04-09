package com.example.demo.migration

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/migration")
class MigrationStatusController(private val flyway: Flyway) {

    @GetMapping("/status")
    fun getStatus(): MigrationStatusResponse {
        val info = flyway.info()
        val all = info.all()
        val applied = all.filter { it.state == MigrationState.SUCCESS }
        val failed = all.filter { it.state == MigrationState.FAILED }
        val pending = all.filter { it.state == MigrationState.PENDING }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val migrations = all.map { m ->
            MigrationInfo(
                version = m.version?.version ?: "repeatable",
                description = m.description ?: "",
                type = m.type?.toString() ?: "UNKNOWN",
                state = m.state?.name ?: "UNKNOWN",
                installedOn = m.installedOn?.let { formatter.format(it.toInstant()) },
                executionTime = m.executionTime
            )
        }

        return MigrationStatusResponse(
            total = all.size,
            applied = applied.size,
            failed = failed.size,
            pending = pending.size,
            currentVersion = info.current()?.version?.version ?: "none",
            migrations = migrations
        )
    }
}

data class MigrationStatusResponse(
    val total: Int,
    val applied: Int,
    val failed: Int,
    val pending: Int,
    val currentVersion: String,
    val migrations: List<MigrationInfo>
)

data class MigrationInfo(
    val version: String,
    val description: String,
    val type: String,
    val state: String,
    val installedOn: String?,
    val executionTime: Int?
)
