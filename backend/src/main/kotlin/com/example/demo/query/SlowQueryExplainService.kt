package com.example.demo.query

import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.PlainSelect
import net.sf.jsqlparser.expression.BinaryExpression
import net.sf.jsqlparser.expression.operators.relational.LikeExpression
import net.sf.jsqlparser.expression.operators.relational.InExpression
import net.sf.jsqlparser.schema.Column
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/**
 * Port for persisting EXPLAIN results.
 * Decoupled from ElasticsearchRepository to keep the service testable
 * without implementing all ES-specific methods.
 */
fun interface ExplainResultStore {
    fun save(result: SlowQueryExplainResult)
}

@Service
class SlowQueryExplainService(
    private val jdbcTemplate: JdbcTemplate,
    private val explainResultStore: ExplainResultStore
) {

    private val log = LoggerFactory.getLogger(SlowQueryExplainService::class.java)

    /**
     * Replaces bound parameters (?) with NULL so that EXPLAIN can parse the query.
     */
    fun bindParamsToNull(sql: String): String =
        sql.replace(Regex("\\?"), "NULL")

    /**
     * Extracts WHERE-clause columns from a SELECT SQL using JSqlParser.
     * Returns a list of "table.column" or just "column" strings.
     */
    fun extractWhereColumns(sql: String): List<Pair<String?, String>> {
        return try {
            val stmt = CCJSqlParserUtil.parse(sql)
            // In JSqlParser 4.7, PlainSelect extends Select directly
            val plainSelect = stmt as? PlainSelect ?: return emptyList()
            val where = plainSelect.where ?: return emptyList()

            val columns = mutableListOf<Pair<String?, String>>()
            collectColumnsFromExpression(where, columns)

            // also check FROM table name for context
            val fromTable = (plainSelect.fromItem as? net.sf.jsqlparser.schema.Table)?.name
            columns.map { (tbl, col) -> Pair(tbl ?: fromTable, col) }
        } catch (e: Exception) {
            log.debug("JSqlParser could not parse SQL for column extraction: {}", e.message)
            emptyList()
        }
    }

    private fun collectColumnsFromExpression(
        expr: net.sf.jsqlparser.expression.Expression,
        result: MutableList<Pair<String?, String>>
    ) {
        when (expr) {
            is BinaryExpression -> {
                val left = expr.leftExpression
                val right = expr.rightExpression
                if (left is Column) {
                    result.add(Pair(left.table?.name, left.columnName))
                }
                if (right is Column) {
                    result.add(Pair(right.table?.name, right.columnName))
                }
                collectColumnsFromExpression(left, result)
                collectColumnsFromExpression(right, result)
            }
            is net.sf.jsqlparser.expression.operators.conditional.AndExpression -> {
                collectColumnsFromExpression(expr.leftExpression, result)
                collectColumnsFromExpression(expr.rightExpression, result)
            }
            is net.sf.jsqlparser.expression.operators.conditional.OrExpression -> {
                collectColumnsFromExpression(expr.leftExpression, result)
                collectColumnsFromExpression(expr.rightExpression, result)
            }
            is InExpression -> {
                val left = expr.leftExpression
                if (left is Column) {
                    result.add(Pair(left.table?.name, left.columnName))
                }
            }
            is LikeExpression -> {
                val left = expr.leftExpression
                if (left is Column) {
                    result.add(Pair(left.table?.name, left.columnName))
                }
            }
        }
    }

    /**
     * Runs EXPLAIN on the given SQL using JdbcTemplate and maps the result rows.
     */
    fun runExplain(sql: String): List<ExplainRow> {
        val nullifiedSql = bindParamsToNull(sql)
        return try {
            jdbcTemplate.query("EXPLAIN $nullifiedSql") { rs, _ ->
                ExplainRow(
                    id = runCatching { rs.getInt("id") }.getOrNull(),
                    selectType = runCatching { rs.getString("select_type") }.getOrNull(),
                    table = runCatching { rs.getString("table") }.getOrNull(),
                    type = runCatching { rs.getString("type") }.getOrNull(),
                    possibleKeys = runCatching { rs.getString("possible_keys") }.getOrNull(),
                    key = runCatching { rs.getString("key") }.getOrNull(),
                    rows = runCatching { rs.getLong("rows") }.getOrNull(),
                    extra = runCatching { rs.getString("Extra") }.getOrNull()
                )
            }
        } catch (e: Exception) {
            log.warn("EXPLAIN failed for SQL [{}]: {}", sql.take(200), e.message)
            emptyList()
        }
    }

    /**
     * Full analysis: run EXPLAIN, detect full table scans, suggest indexes, persist to ES.
     */
    fun analyzeSlowQuery(sql: String, executionTimeMs: Long): SlowQueryExplainResult {
        val explainRows = runExplain(sql)
        val hasFullTableScan = explainRows.any { it.type == "ALL" }

        val whereColumns = extractWhereColumns(bindParamsToNull(sql))
        val recommendations = whereColumns
            .distinctBy { it }
            .map { (table, column) ->
                if (table != null) "Consider adding index on $table.$column"
                else "Consider adding index on $column"
            }

        val result = SlowQueryExplainResult(
            id = UUID.randomUUID().toString(),
            originalSql = sql,
            explainRows = explainRows,
            indexRecommendations = recommendations,
            hasFullTableScan = hasFullTableScan,
            capturedAt = LocalDateTime.now(),
            executionTimeMs = executionTimeMs
        )

        try {
            explainResultStore.save(result)
        } catch (e: Exception) {
            log.warn("Failed to persist SlowQueryExplainResult to Elasticsearch: {}", e.message)
        }

        return result
    }
}
