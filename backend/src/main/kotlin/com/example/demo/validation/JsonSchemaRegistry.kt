package com.example.demo.validation

import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface SchemaRegistry {
    fun getSchema(path: String): JsonSchema
}

@Component
class JsonSchemaRegistry : SchemaRegistry {

    private val factory: JsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    private val cache = ConcurrentHashMap<String, JsonSchema>()

    override fun getSchema(path: String): JsonSchema {
        return cache.getOrPut(path) {
            val resource = ClassPathResource(path)
            if (!resource.exists()) {
                throw IllegalArgumentException("JSON Schema not found at classpath: $path")
            }
            resource.inputStream.use { factory.getSchema(it) }
        }
    }
}
