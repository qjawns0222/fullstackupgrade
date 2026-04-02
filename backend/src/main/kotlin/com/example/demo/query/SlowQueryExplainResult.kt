package com.example.demo.query

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

@Document(indexName = "slow-query-explain")
data class SlowQueryExplainResult(
    @Id
    val id: String,

    @Field(type = FieldType.Text)
    val originalSql: String,

    @Field(type = FieldType.Object)
    val explainRows: List<ExplainRow>,

    @Field(type = FieldType.Keyword)
    val indexRecommendations: List<String>,

    @Field(type = FieldType.Boolean)
    val hasFullTableScan: Boolean,

    @Field(type = FieldType.Date, format = [], pattern = ["yyyy-MM-dd'T'HH:mm:ss.SSS"])
    val capturedAt: LocalDateTime,

    @Field(type = FieldType.Long)
    val executionTimeMs: Long
)
