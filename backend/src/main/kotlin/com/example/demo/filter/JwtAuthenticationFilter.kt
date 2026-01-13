package com.example.demo.filter

import com.example.demo.security.JwtTokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // val token = jwtTokenProvider.resolveToken(request)

        // if (token != null && jwtTokenProvider.validateToken(token)) {
        //     val auth = jwtTokenProvider.getAuthentication(token)
        //     SecurityContextHolder.getContext().authentication = auth
        // }

        filterChain.doFilter(request, response)
    }
}
