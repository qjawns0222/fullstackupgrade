package com.example.demo.validation

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedDeque

interface ViolationStore {
    fun record(violation: SchemaViolation)
    fun getRecent(limit: Int): List<SchemaViolation>
    fun getStats(): ViolationStats
    fun clear()
}

data class ViolationStats(
    val total: Long,
    val bySchema: Map<String, Long>,
    val byEndpoint: Map<String, Long>
)

@Component
class SchemaViolationStore : ViolationStore {

    private val store = ConcurrentLinkedDeque<SchemaViolation>()
    private val maxSize = 500

    override fun record(violation: SchemaViolation) {
        store.addFirst(violation)
        while (store.size > maxSize) {
            store.pollLast()
        }
    }

    override fun getRecent(limit: Int): List<SchemaViolation> {
        return store.take(limit)
    }

    override fun getStats(): ViolationStats {
        val snapshot = store.toList()
        return ViolationStats(
            total = snapshot.size.toLong(),
            bySchema = snapshot.groupingBy { it.schemaPath }.eachCount().mapValues { it.value.toLong() },
            byEndpoint = snapshot.groupingBy { "${it.method} ${it.endpoint}" }.eachCount()
                .mapValues { it.value.toLong() }
        )
    }

    override fun clear() {
        store.clear()
    }
}
