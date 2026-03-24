package com.example.demo.logging

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

/**
 * Elasticsearch document that captures every HTTP request's correlation metadata.
 * Stored in the `http_access_logs` index and queryable from the admin log-viewer UI.
 */
@Document(indexName = "http_access_logs")
data class HttpAccessLogDocument(
    @Id val id: String? = null,
    @Field(type = FieldType.Keyword) val requestId: String,
    @Field(type = FieldType.Keyword) val method: String,
    @Field(type = FieldType.Keyword) val path: String,
    @Field(type = FieldType.Integer) val status: Int,
    @Field(type = FieldType.Long) val durationMs: Long,
    @Field(type = FieldType.Keyword) val clientIp: String,
    @Field(type = FieldType.Keyword) val userId: String? = null,
    @Field(type = FieldType.Date, format = [], pattern = ["yyyy-MM-dd'T'HH:mm:ss.SSS"])
    val timestamp: LocalDateTime = LocalDateTime.now()
)
