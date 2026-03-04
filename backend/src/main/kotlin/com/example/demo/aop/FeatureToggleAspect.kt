package com.example.demo.aop

import com.example.demo.annotation.FeatureToggle
import com.example.demo.exception.FeatureDisabledException
import io.getunleash.Unleash
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class FeatureToggleAspect(private val unleash: Unleash) {

    @Around("@annotation(featureToggle)")
    fun checkFeature(joinPoint: ProceedingJoinPoint, featureToggle: FeatureToggle): Any? {
        val featureName = featureToggle.name

        if (!unleash.isEnabled(featureName)) {
            throw FeatureDisabledException(featureName)
        }

        return joinPoint.proceed()
    }
}
