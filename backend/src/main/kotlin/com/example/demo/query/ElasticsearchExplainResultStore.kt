package com.example.demo.query

import org.springframework.stereotype.Component

/**
 * Adapter that bridges ExplainResultStore (simple port) → SlowQueryExplainRepository (ES).
 * This keeps SlowQueryExplainService decoupled from the full ElasticsearchRepository interface.
 */
@Component
class ElasticsearchExplainResultStore(
    private val repository: SlowQueryExplainRepository
) : ExplainResultStore {

    override fun save(result: SlowQueryExplainResult) {
        repository.save(result)
    }
}
