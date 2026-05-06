package com.example.demo.query

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class QueryHintSummary(
    val totalRegistered: Int,
    val entries: List<QueryHintEntryDto>
)

data class QueryHintEntryDto(
    val normalizedSql: String,
    val hint: String,
    val slowCount: Int,
    val registeredAt: String
)

@RestController
@RequestMapping("/api/query-hints")
class QueryHintController(private val registry: QueryHintRegistry) {

    @GetMapping
    fun summary(): QueryHintSummary {
        val entries = registry.allEntries().map { e ->
            QueryHintEntryDto(
                normalizedSql = e.normalizedSql,
                hint = e.hint,
                slowCount = e.slowCount,
                registeredAt = e.registeredAt.toString()
            )
        }
        return QueryHintSummary(totalRegistered = entries.size, entries = entries)
    }

    @DeleteMapping
    fun remove(@RequestParam sql: String): ResponseEntity<Void> {
        registry.remove(sql)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/all")
    fun clearAll(): ResponseEntity<Void> {
        registry.clear()
        return ResponseEntity.noContent().build()
    }
}
