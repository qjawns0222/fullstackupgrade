package com.example.demo.service

import com.example.demo.dto.TokenDto
import com.example.demo.entity.RefreshToken
import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.security.JwtTokenProvider
import com.example.demo.security.TokenInfo
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

        @Mock lateinit var authenticationManager: AuthenticationManager

        @Mock lateinit var jwtTokenProvider: JwtTokenProvider

        @Mock lateinit var refreshTokenRepository: RefreshTokenRepository

        @Mock lateinit var customUserDetailsService: CustomUserDetailsService

        @Mock lateinit var userRepository: com.example.demo.repository.UserRepository

        @Mock lateinit var mfaService: MfaService

        @InjectMocks lateinit var authService: AuthService

        // Helper to avoid NullPointerException with Mockito.any() in Kotlin
        private fun <T> any(type: Class<T>): T {
                Mockito.any(type)
                return null as T
        }

        @Test
        fun `reissue - Successful Token Rotation`() {
                // Given
                val oldRefreshToken = "old_refresh_token"
                val newRefreshToken = "new_refresh_token"
                val newAccessToken = "new_access_token"
                val username = "testuser"
                val role = "ROLE_USER"

                val savedRefreshToken =
                        RefreshToken(refreshToken = oldRefreshToken, username = username)
                val userDetails =
                        User.withUsername(username)
                                .password("password")
                                .authorities(SimpleGrantedAuthority(role))
                                .build()
                // val newAuthentication = UsernamePasswordAuthenticationToken(userDetails, "",
                // userDetails.authorities)

                val newTokenInfo =
                        TokenInfo(
                                grantType = "Bearer",
                                accessToken = newAccessToken,
                                refreshToken = newRefreshToken,
                                accessTokenExpiresIn = 3600000
                        )

                // Mock behaviors
                `when`(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true)
                `when`(jwtTokenProvider.getSubject(oldRefreshToken)).thenReturn(username)
                `when`(refreshTokenRepository.findById(oldRefreshToken))
                        .thenReturn(Optional.of(savedRefreshToken))
                `when`(customUserDetailsService.loadUserByUsername(username))
                        .thenReturn(userDetails)

                // Use helper any()
                `when`(jwtTokenProvider.createToken(any(Authentication::class.java)))
                        .thenReturn(newTokenInfo)

                // When
                val result: TokenDto = authService.reissue(oldRefreshToken)

                // Then
                assertNotNull(result)
                assertEquals(newAccessToken, result.accessToken)
                assertEquals(newRefreshToken, result.refreshToken)

                // Verify interactions (Rotation Logic)
                verify(refreshTokenRepository).delete(savedRefreshToken) // Old token deleted
                // Use helper any()
                verify(refreshTokenRepository)
                        .save(any(RefreshToken::class.java)) // New token saved
        }

        @Test
        fun `reissue - Fail if Token Invalid`() {
                // Given
                val invalidToken = "invalid_token"
                `when`(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false)

                // When & Then
                val exception =
                        assertThrows(RuntimeException::class.java) {
                                authService.reissue(invalidToken)
                        }
                assertEquals("Refresh Token이 유효하지 않습니다.", exception.message)
        }

        @Test
        fun `reissue - Fail if Token Not in Redis (Logged out or Reused)`() {
                // Given
                val oldRefreshToken = "missing_token"
                val username = "testuser"
                `when`(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true)
                `when`(jwtTokenProvider.getSubject(oldRefreshToken)).thenReturn(username)
                `when`(refreshTokenRepository.findById(oldRefreshToken))
                        .thenReturn(Optional.empty())

                // When & Then
                val exception =
                        assertThrows(RuntimeException::class.java) {
                                authService.reissue(oldRefreshToken)
                        }
                assertEquals("로그아웃 된 사용자이거나, 이미 만료/사용된 토큰입니다.", exception.message)
        }
}
