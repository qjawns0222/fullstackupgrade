package com.example.demo.util;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.PathBuilder;
import org.springframework.util.StringUtils;

/**
 * QueryDSL 유틸리티 클래스
 */
public class QueryDslUtil {

    /**
     * sortBy와 direction 파라미터를 받아 OrderSpecifier를 동적으로 생성
     *
     * @param qEntity   QueryDSL Q-Class 인스턴스 (예: QUser.user)
     * @param sortBy    정렬 기준 필드명
     * @param direction 정렬 방향 (asc, desc)
     * @return OrderSpecifier<?> 또는 null (sortBy가 비어있을 경우)
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static OrderSpecifier<?> getOrderSpecifier(EntityPathBase<?> qEntity, String sortBy, String direction) {
        if (!StringUtils.hasText(sortBy)) {
            return null;
        }

        Order order = "desc".equalsIgnoreCase(direction) ? Order.DESC : Order.ASC;
        PathBuilder<?> pathBuilder = new PathBuilder<>(qEntity.getType(), qEntity.getMetadata());

        return new OrderSpecifier(order, pathBuilder.get(sortBy));
    }
}
