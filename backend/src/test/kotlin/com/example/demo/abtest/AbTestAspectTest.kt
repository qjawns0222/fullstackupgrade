package com.example.demo.abtest

import com.example.demo.annotation.ABTest
import io.getunleash.FakeUnleash
import io.getunleash.Variant
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class AbTestAspectTest {

    private lateinit var fakeUnleash: FakeUnleash
    private lateinit var fakeStore: FakeAbTestStore
    private lateinit var service: AbTestService
    private lateinit var aspect: AbTestAspect
    private lateinit var joinPoint: ProceedingJoinPoint

    @BeforeEach
    fun setUp() {
        fakeUnleash = FakeUnleash()
        fakeStore = FakeAbTestStore()
        service = AbTestService(fakeUnleash, fakeStore)
        aspect = AbTestAspect(fakeUnleash, service)
        joinPoint = mock(ProceedingJoinPoint::class.java)
    }

    @Test
    fun `applyVariant proceeds and records event when trackEvent is true`() {
        fakeUnleash.enable("checkout-ab")
        fakeUnleash.setVariant("checkout-ab", Variant("B", null as String?, true))
        `when`(joinPoint.proceed()).thenReturn("result")

        val abTest = mock(ABTest::class.java)
        `when`(abTest.toggleName).thenReturn("checkout-ab")
        `when`(abTest.trackEvent).thenReturn(true)

        val result = aspect.applyVariant(joinPoint, abTest)

        assertEquals("result", result)
        assertEquals(1, fakeStore.saved.size)
        assertEquals("B", fakeStore.saved.first().variantName)
    }

    @Test
    fun `applyVariant proceeds without recording when trackEvent is false`() {
        fakeUnleash.enable("silent-ab")
        fakeUnleash.setVariant("silent-ab", Variant("A", null as String?, true))
        `when`(joinPoint.proceed()).thenReturn("ok")

        val abTest = mock(ABTest::class.java)
        `when`(abTest.toggleName).thenReturn("silent-ab")
        `when`(abTest.trackEvent).thenReturn(false)

        aspect.applyVariant(joinPoint, abTest)

        assertEquals(0, fakeStore.saved.size)
    }

    @Test
    fun `AbTestVariantHolder is cleared after proceed`() {
        fakeUnleash.enable("flag")
        fakeUnleash.setVariant("flag", Variant("C", null as String?, true))
        `when`(joinPoint.proceed()).thenReturn(null)

        val abTest = mock(ABTest::class.java)
        `when`(abTest.toggleName).thenReturn("flag")
        `when`(abTest.trackEvent).thenReturn(false)

        aspect.applyVariant(joinPoint, abTest)

        assertNull(AbTestVariantHolder.get())
    }

    @Test
    fun `AbTestVariantHolder is cleared even when proceed throws`() {
        fakeUnleash.enable("flag")
        fakeUnleash.setVariant("flag", Variant("A", null as String?, true))
        `when`(joinPoint.proceed()).thenThrow(RuntimeException("boom"))

        val abTest = mock(ABTest::class.java)
        `when`(abTest.toggleName).thenReturn("flag")
        `when`(abTest.trackEvent).thenReturn(false)

        runCatching { aspect.applyVariant(joinPoint, abTest) }

        assertNull(AbTestVariantHolder.get())
    }
}
