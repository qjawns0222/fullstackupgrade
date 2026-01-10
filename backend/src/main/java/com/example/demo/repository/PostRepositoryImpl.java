package com.example.demo.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.User;
import com.example.demo.entity.QUser;

@Repository
@Transactional(readOnly = true)
public class PostRepositoryImpl implements PostRepositoryCustom {

    private JPAQueryFactory queryFactory;

    public PostRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    @Cacheable(value = "searchConditionCache", key = "'defaultCondition'")
    public List<User> searchByCondition() {
        return queryFactory.selectFrom(QUser.user)
                .where(QUser.user.password.contains("12345"))
                .fetch();

    }

}
