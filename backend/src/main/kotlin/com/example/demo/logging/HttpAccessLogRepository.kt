package com.example.demo.logging

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface HttpAccessLogRepository : ElasticsearchRepository<HttpAccessLogDocument, String> {

    fun findByPath(path: String, pageable: Pageable): Page<HttpAccessLogDocument>

    fun findByStatus(status: Int, pageable: Pageable): Page<HttpAccessLogDocument>

    fun findByUserId(userId: String, pageable: Pageable): Page<HttpAccessLogDocument>

    fun findByMethodAndStatus(method: String, status: Int, pageable: Pageable): Page<HttpAccessLogDocument>
}
