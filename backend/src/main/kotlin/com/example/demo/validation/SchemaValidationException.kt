package com.example.demo.validation

class SchemaValidationException(
    val violations: List<String>,
    val schemaPath: String
) : RuntimeException("JSON Schema validation failed for schema '$schemaPath': ${violations.joinToString("; ")}")
