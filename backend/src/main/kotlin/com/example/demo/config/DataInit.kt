package com.example.demo.config

import com.example.demo.entity.User
import com.example.demo.repository.UserRepository
import jakarta.annotation.PostConstruct
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DataInit(
        private val userRepository: UserRepository,
        private val passwordEncoder: PasswordEncoder
) {

    @PostConstruct
    fun init() {
        if (userRepository.findByUsername("testuser1234").isEmpty) {
            val user =
                    User(
                            username = "testuser1234",
                            password = passwordEncoder.encode("222222"),
                            role = "ROLE_USER"
                    )
            userRepository.save(user)
        }
    }
}
