package com.example.demo.abtest

import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class VariantStats(
    val toggleName: String,
    val variants: Map<String, Long>,
    val total: Long,
    val periodHours: Int
)

@Service
class AbTestService(
    private val unleash: Unleash,
    private val store: AbTestStore
) {
    fun getVariant(toggleName: String, userId: String? = null, sessionId: String? = null): AbTestResult {
        val context = if (userId != null) {
            UnleashContext.builder().userId(userId).build()
        } else {
            UnleashContext.builder().build()
        }

        val variant = unleash.getVariant(toggleName, context)
        val payload = variant.payload.map { it.value }.orElse(null)

        val result = AbTestResult(
            toggleName = toggleName,
            variantName = variant.name,
            userId = userId,
            sessionId = sessionId,
            payload = payload
        )
        return store.save(result)
    }

    fun recordVariant(
        toggleName: String,
        variantName: String,
        userId: String?,
        sessionId: String?,
        payload: String?
    ): AbTestResult = store.save(
        AbTestResult(
            toggleName = toggleName,
            variantName = variantName,
            userId = userId,
            sessionId = sessionId,
            payload = payload
        )
    )

    fun getStats(toggleName: String, periodHours: Int = 24): VariantStats {
        val since = LocalDateTime.now().minusHours(periodHours.toLong())
        val counts = store.countByToggleAndVariantSince(toggleName, since)
        return VariantStats(
            toggleName = toggleName,
            variants = counts,
            total = counts.values.sum(),
            periodHours = periodHours
        )
    }

    fun getRecentResults(toggleName: String, limit: Int = 50): List<AbTestResult> =
        store.findRecentByToggle(toggleName, limit)
}
