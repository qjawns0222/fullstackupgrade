package com.example.demo.query

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface SlowQueryExplainRepository : ElasticsearchRepository<SlowQueryExplainResult, String>
