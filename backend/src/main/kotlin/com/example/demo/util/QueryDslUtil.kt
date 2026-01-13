package com.example.demo.util

import com.querydsl.core.types.Order
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.EntityPathBase
import com.querydsl.core.types.dsl.PathBuilder
import org.springframework.util.StringUtils

object QueryDslUtil {

    /**
     * sortBy와 direction 파라미터를 받아 OrderSpecifier를 동적으로 생성
     *
     * @param qEntity   QueryDSL Q-Class 인스턴스 (예: QUser.user)
     * @param sortBy    정렬 기준 필드명
     * @param direction 정렬 방향 (asc, desc)
     * @return OrderSpecifier<?> 또는 null (sortBy가 비어있을 경우)
     */
    fun getOrderSpecifier(qEntity: EntityPathBase<*>, sortBy: String?, direction: String?): OrderSpecifier<*>? {
        if (!StringUtils.hasText(sortBy)) {
            return null
        }

        val order = if ("desc".equals(direction, ignoreCase = true)) Order.DESC else Order.ASC
        val pathBuilder = PathBuilder(qEntity.type, qEntity.metadata)

        @Suppress("UNCHECKED_CAST")
        return OrderSpecifier(order, pathBuilder.get(sortBy) as com.querydsl.core.types.Expression<Comparable<*>>)
    }
}
