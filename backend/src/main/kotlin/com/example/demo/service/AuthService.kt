package com.example.demo.service

import com.example.demo.dto.LoginRequest
import com.example.demo.dto.TokenDto
import com.example.demo.entity.RefreshToken
import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
        private val authenticationManager: AuthenticationManager,
        private val jwtTokenProvider: JwtTokenProvider,
        private val refreshTokenRepository: RefreshTokenRepository
) {

    @Transactional
    fun login(loginRequest: LoginRequest): TokenDto {
        val authenticationToken =
                UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password)
        val authentication = authenticationManager.authenticate(authenticationToken)
        val tokenInfo = jwtTokenProvider.createToken(authentication)

        // Save Refresh Token
        refreshTokenRepository.save(
                RefreshToken(refreshToken = tokenInfo.refreshToken, username = authentication.name)
        )

        return TokenDto(
                grantType = tokenInfo.grantType,
                accessToken = tokenInfo.accessToken,
                refreshToken = tokenInfo.refreshToken,
                accessTokenExpiresIn = tokenInfo.accessTokenExpiresIn
        )
    }

    @Transactional
    fun reissue(refreshToken: String): TokenDto {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw RuntimeException("Refresh Token이 유효하지 않습니다.")
        }

        val authentication = jwtTokenProvider.getAuthentication(refreshToken)
        val savedRefreshToken =
                refreshTokenRepository.findById(refreshToken).orElseThrow {
                    RuntimeException("로그아웃 된 사용자입니다.")
                }

        if (savedRefreshToken.username != authentication.name) {
            throw RuntimeException("토큰의 유저 정보가 일치하지 않습니다.")
        }

        val tokenInfo = jwtTokenProvider.createToken(authentication)

        refreshTokenRepository.delete(savedRefreshToken)
        refreshTokenRepository.save(
                RefreshToken(refreshToken = tokenInfo.refreshToken, username = authentication.name)
        )

        return TokenDto(
                grantType = tokenInfo.grantType,
                accessToken = tokenInfo.accessToken,
                refreshToken = tokenInfo.refreshToken,
                accessTokenExpiresIn = tokenInfo.accessTokenExpiresIn
        )
    }
}
