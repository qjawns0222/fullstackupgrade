package com.example.demo.query

data class ExplainRow(
    val id: Int?,
    val selectType: String?,
    val table: String?,
    val type: String?,          // "ALL" = full table scan risk
    val possibleKeys: String?,
    val key: String?,
    val rows: Long?,
    val extra: String?
)
