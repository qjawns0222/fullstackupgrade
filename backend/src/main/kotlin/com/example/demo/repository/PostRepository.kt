package com.example.demo.repository

import com.example.demo.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface PostRepository : JpaRepository<User, Long>, PostRepositoryCustom
