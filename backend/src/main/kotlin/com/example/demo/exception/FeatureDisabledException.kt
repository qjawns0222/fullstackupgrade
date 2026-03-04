package com.example.demo.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
class FeatureDisabledException(featureName: String) :
        RuntimeException("Feature '$featureName' is currently disabled.")
