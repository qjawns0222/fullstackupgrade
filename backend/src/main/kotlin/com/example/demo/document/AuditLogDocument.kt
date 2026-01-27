package com.example.demo.document

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType

@Document(indexName = "audit_logs")
data class AuditLogDocument(
        @Id val id: String? = null,
        @Field(type = FieldType.Keyword) val userId: String,
        @Field(type = FieldType.Keyword) val action: String,
        @Field(type = FieldType.Text) val description: String,
        @Field(type = FieldType.Text) val params: String,
        @Field(type = FieldType.Keyword) val status: String,
        @Field(type = FieldType.Text) val errorMessage: String? = null,
        @Field(type = FieldType.Date, format = [], pattern = ["yyyy-MM-dd'T'HH:mm:ss.SSS"])
        val timestamp: LocalDateTime = LocalDateTime.now()
)
