package com.example.demo.repository

import com.example.demo.entity.RefreshToken
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository interface RefreshTokenRepository : CrudRepository<RefreshToken, String>
