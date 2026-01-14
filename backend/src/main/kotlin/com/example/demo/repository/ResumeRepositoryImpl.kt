package com.example.demo.repository

import com.example.demo.entity.QResume.resume
import com.example.demo.entity.Resume
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class ResumeRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : ResumeRepositoryCustom {

    override fun search(keyword: String?, pageable: Pageable): Page<Resume> {
        val query = queryFactory
            .selectFrom(resume)
            .where(
                containsKeyword(keyword)
            )
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())

        val results = query.fetch()
        val total = queryFactory
            .select(resume.count())
            .from(resume)
            .where(containsKeyword(keyword))
            .fetchOne() ?: 0L

        return PageImpl(results, pageable, total)
    }

    private fun containsKeyword(keyword: String?): BooleanExpression? {
        return if (keyword.isNullOrBlank()) {
            null
        } else {
            resume.content.containsIgnoreCase(keyword)
                .or(resume.originalFileName.containsIgnoreCase(keyword))
        }
    }
}
