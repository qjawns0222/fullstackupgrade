package com.example.demo.repository

import com.example.demo.document.ResumeDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface ResumeSearchRepository : ElasticsearchRepository<ResumeDocument, Long> {
    fun findByContentContaining(content: String): List<ResumeDocument>
    fun findByContentChosungContaining(chosung: String): List<ResumeDocument>
}
