package com.example.demo.query

import org.hibernate.resource.jdbc.spi.StatementInspector

/**
 * Hibernate StatementInspector that prepends optimizer hint comments
 * for SQL patterns registered in QueryHintRegistry.
 */
class QueryHintInterceptor(private val registry: QueryHintRegistry) : StatementInspector {

    override fun inspect(sql: String): String {
        val normalized = QueryExecutionContext.normalize(sql)
        val hint = registry.getHint(normalized) ?: return sql
        return "$hint $sql"
    }
}
