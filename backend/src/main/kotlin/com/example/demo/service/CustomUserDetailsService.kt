package com.example.demo.service

import com.example.demo.entity.User
import com.example.demo.repository.UserRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomUserDetailsService(private val userRepository: UserRepository) : UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user =
                userRepository.findByUsername(username).orElseThrow {
                    UsernameNotFoundException("User not found with username: $username")
                }

        val authorities: List<GrantedAuthority> = listOf(SimpleGrantedAuthority(user.role))

        return org.springframework.security.core.userdetails.User.withUsername(user.username)
                .password(user.password)
                .authorities(authorities)
                .build()
    }
}
