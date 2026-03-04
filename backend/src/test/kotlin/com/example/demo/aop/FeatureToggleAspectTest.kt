package com.example.demo.aop

import com.example.demo.annotation.FeatureToggle
import com.example.demo.exception.FeatureDisabledException
import io.getunleash.Unleash
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*

class FeatureToggleAspectTest {

    private val unleash = mock(Unleash::class.java)
    private val aspect = FeatureToggleAspect(unleash)
    private val joinPoint = mock(ProceedingJoinPoint::class.java)
    private val featureToggle = mock(FeatureToggle::class.java)

    @Test
    fun `should proceed when feature is enabled`() {
        // Given
        `when`(featureToggle.name).thenReturn("test-feature")
        `when`(unleash.isEnabled("test-feature")).thenReturn(true)
        `when`(joinPoint.proceed()).thenReturn("Success")

        // When
        val result = aspect.checkFeature(joinPoint, featureToggle)

        // Then
        assertEquals("Success", result)
        verify(joinPoint).proceed()
    }

    @Test
    fun `should throw exception when feature is disabled`() {
        // Given
        `when`(featureToggle.name).thenReturn("test-feature")
        `when`(unleash.isEnabled("test-feature")).thenReturn(false)

        // When & Then
        assertThrows<FeatureDisabledException> { aspect.checkFeature(joinPoint, featureToggle) }
        verify(joinPoint, never()).proceed()
    }
}
