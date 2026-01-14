package com.example.demo.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Mapping
import org.springframework.data.elasticsearch.annotations.Setting

@Document(indexName = "resumes")
@Setting(settingPath = "/es/es-setting.json")
@Mapping(mappingPath = "/es/es-mapping.json")
class ResumeDocument(
        @Id val id: Long,
        val originalFileName: String,
        val content: String?,
        val contentChosung: String?
)
