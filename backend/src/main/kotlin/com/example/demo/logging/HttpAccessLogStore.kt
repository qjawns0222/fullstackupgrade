package com.example.demo.logging

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

/**
 * Thin interface over the Elasticsearch repository, kept narrow to make
 * unit-testing the service layer straightforward without mocking complex
 * Spring Data ES interfaces.
 */
interface HttpAccessLogStore {
    fun save(doc: HttpAccessLogDocument): HttpAccessLogDocument
    fun findRecent(page: Int, size: Int): Page<HttpAccessLogDocument>
    fun findByStatus(status: Int, page: Int, size: Int): Page<HttpAccessLogDocument>
    fun findByUserId(userId: String, page: Int, size: Int): Page<HttpAccessLogDocument>
    fun count(): Long
}

@Component
class ElasticsearchHttpAccessLogStore(
    private val repository: HttpAccessLogRepository
) : HttpAccessLogStore {

    private fun desc() = Sort.by(Sort.Direction.DESC, "timestamp")

    override fun save(doc: HttpAccessLogDocument): HttpAccessLogDocument = repository.save(doc)

    override fun findRecent(page: Int, size: Int): Page<HttpAccessLogDocument> =
        repository.findAll(PageRequest.of(page, size, desc()))

    override fun findByStatus(status: Int, page: Int, size: Int): Page<HttpAccessLogDocument> =
        repository.findByStatus(status, PageRequest.of(page, size, desc()))

    override fun findByUserId(userId: String, page: Int, size: Int): Page<HttpAccessLogDocument> =
        repository.findByUserId(userId, PageRequest.of(page, size, desc()))

    override fun count(): Long = repository.count()
}
