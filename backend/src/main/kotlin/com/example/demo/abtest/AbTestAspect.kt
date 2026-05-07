package com.example.demo.abtest

import com.example.demo.annotation.ABTest
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class AbTestAspect(
    private val unleash: Unleash,
    private val service: AbTestService
) {
    @Around("@annotation(abTest)")
    fun applyVariant(joinPoint: ProceedingJoinPoint, abTest: ABTest): Any? {
        val context = UnleashContext.builder().build()
        val variant = unleash.getVariant(abTest.toggleName, context)

        AbTestVariantHolder.set(variant.name)
        return try {
            val result = joinPoint.proceed()
            if (abTest.trackEvent) {
                val payload = variant.payload.map { it.value }.orElse(null)
                service.recordVariant(
                    toggleName = abTest.toggleName,
                    variantName = variant.name,
                    userId = null,
                    sessionId = null,
                    payload = payload
                )
            }
            result
        } finally {
            AbTestVariantHolder.clear()
        }
    }
}

object AbTestVariantHolder {
    private val holder = ThreadLocal<String>()

    fun set(variant: String) = holder.set(variant)
    fun get(): String? = holder.get()
    fun clear() = holder.remove()
}
