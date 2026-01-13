package com.example.demo.repository

import com.example.demo.entity.QUser
import com.example.demo.entity.User
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class PostRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : PostRepositoryCustom {

    @Cacheable(value = ["searchConditionCache"], key = "'defaultCondition'")
    override fun searchByCondition(): List<User> {
        return queryFactory.selectFrom(QUser.user)
            .where(QUser.user.password.contains("12345"))
            .fetch()
    }
}
