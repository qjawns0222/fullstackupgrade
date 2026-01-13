package com.example.demo.repository

import com.example.demo.entity.User

interface PostRepositoryCustom {
    fun searchByCondition(): List<User>
}
