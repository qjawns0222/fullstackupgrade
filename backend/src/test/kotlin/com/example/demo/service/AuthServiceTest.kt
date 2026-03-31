package com.example.demo.service

import com.example.demo.dto.TokenDto
import com.example.demo.entity.RefreshToken
import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.security.JwtTokenProvider
import com.example.demo.security.TokenBlacklistService
import com.example.demo.security.TokenInfo
import java.util.Date
import java.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User

/**
 * Fake implementation of TokenBlacklistService for tests.
 * Avoids Mockito/Kotlin NPE issues with non-null constructor parameters
 * and with any() argument matchers that pollute Mockito's internal stack.
 */
class FakeTokenBlacklistService : TokenBlacklistService {
        val blacklistedTokens = mutableMapOf<String, Long>()
        var blacklistCallCount = 0

        override fun blacklist(token: String, remainingTtlSeconds: Long) {
                blacklistCallCount++
                if (remainingTtlSeconds > 0) {
                        blacklistedTokens[token] = remainingTtlSeconds
                }
        }

        override fun isBlacklisted(token: String): Boolean = token in blacklistedTokens

        override fun blacklistSize(): Long = blacklistedTokens.size.toLong()
}

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

        @Mock lateinit var authenticationManager: AuthenticationManager
        @Mock lateinit var jwtTokenProvider: JwtTokenProvider
        @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
        @Mock lateinit var customUserDetailsService: CustomUserDetailsService
        @Mock lateinit var userRepository: com.example.demo.repository.UserRepository
        @Mock lateinit var mfaService: MfaService

        private lateinit var fakeBlacklist: FakeTokenBlacklistService
        private lateinit var authService: AuthService

        @BeforeEach
        fun setUp() {
                fakeBlacklist = FakeTokenBlacklistService()
                authService = AuthService(
                        authenticationManager = authenticationManager,
                        jwtTokenProvider = jwtTokenProvider,
                        refreshTokenRepository = refreshTokenRepository,
                        customUserDetailsService = customUserDetailsService,
                        userRepository = userRepository,
                        mfaService = mfaService,
                        tokenBlacklistService = fakeBlacklist
                )
        }

        /**
         * Helper for Mockito's any() to avoid NullPointerException with Kotlin's non-nullable parameters.
         * Mockito.any() returns null, which triggers Kotlin's Intrinsics.checkParameterIsNotNull.
         */
        private fun <T> anyKotlin(): T {
                org.mockito.ArgumentMatchers.any<T>()
                return uninitialized()
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> uninitialized(): T = null as T

        // ─── Reissue tests ───────────────────────────────────────────────────────

        @Test
        fun `reissue - Successful Token Rotation`() {
                val oldRefreshToken = "old_refresh_token"
                val newRefreshToken = "new_refresh_token"
                val newAccessToken = "new_access_token"
                val username = "testuser"

                val savedRefreshToken = RefreshToken(refreshToken = oldRefreshToken, username = username)
                val userDetails = User.withUsername(username)
                        .password("password")
                        .authorities(SimpleGrantedAuthority("ROLE_USER"))
                        .build()
                val newTokenInfo = TokenInfo(
                        grantType = "Bearer",
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        accessTokenExpiresIn = 3600000
                )

                `when`(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true)
                `when`(jwtTokenProvider.getSubject(oldRefreshToken)).thenReturn(username)
                `when`(refreshTokenRepository.findById(oldRefreshToken)).thenReturn(Optional.of(savedRefreshToken))
                `when`(customUserDetailsService.loadUserByUsername(username)).thenReturn(userDetails)
                
                // Use anyKotlin() to avoid NPE on createToken(null)
                doAnswer { newTokenInfo }.`when`(jwtTokenProvider).createToken(anyKotlin())

                val result: TokenDto = authService.reissue(oldRefreshToken)

                assertNotNull(result)
                assertEquals(newAccessToken, result.accessToken)
                assertEquals(newRefreshToken, result.refreshToken)
                verify(refreshTokenRepository).delete(savedRefreshToken)
                verify(refreshTokenRepository).save(RefreshToken(refreshToken = newRefreshToken, username = username))
        }

        @Test
        fun `reissue - Fail if Token Invalid`() {
                val invalidToken = "invalid_token"
                `when`(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false)

                val exception = assertThrows(RuntimeException::class.java) {
                        authService.reissue(invalidToken)
                }
                assertEquals("Refresh Token이 유효하지 않습니다.", exception.message)
        }

        @Test
        fun `reissue - Fail if Token Not in Redis (Logged out or Reused)`() {
                // Ensure no leftover state from previous failed stubbing
                org.mockito.Mockito.reset(jwtTokenProvider, refreshTokenRepository)

                val oldRefreshToken = "missing_token"
                val username = "testuser"
                `when`(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true)
                `when`(jwtTokenProvider.getSubject(oldRefreshToken)).thenReturn(username)
                `when`(refreshTokenRepository.findById(oldRefreshToken)).thenReturn(Optional.empty())

                val exception = assertThrows(RuntimeException::class.java) {
                        authService.reissue(oldRefreshToken)
                }
                assertEquals("로그아웃 된 사용자이거나, 이미 만료/사용된 토큰입니다.", exception.message)
        }

        // ─── Logout tests ────────────────────────────────────────────────────────

        @Test
        fun `logout - blacklists access token and deletes refresh token`() {
                val accessToken = "valid.access.token"
                val refreshToken = "stored.refresh.token"
                val username = "testuser"
                val expirationDate = Date(System.currentTimeMillis() + 1000 * 900)
                val savedRefreshToken = RefreshToken(refreshToken = refreshToken, username = username)

                `when`(jwtTokenProvider.validateToken(accessToken)).thenReturn(true)
                `when`(jwtTokenProvider.getExpiration(accessToken)).thenReturn(expirationDate)
                `when`(refreshTokenRepository.findById(refreshToken)).thenReturn(Optional.of(savedRefreshToken))

                authService.logout(accessToken, refreshToken)

                assertTrue(fakeBlacklist.blacklistedTokens.containsKey(accessToken))
                assertTrue((fakeBlacklist.blacklistedTokens[accessToken] ?: 0) > 0)
                verify(refreshTokenRepository).delete(savedRefreshToken)
        }

        @Test
        fun `logout - skips blacklist when access token is already invalid`() {
                val expiredAccessToken = "expired.access.token"
                `when`(jwtTokenProvider.validateToken(expiredAccessToken)).thenReturn(false)

                authService.logout(expiredAccessToken, null)

                assertEquals(0, fakeBlacklist.blacklistCallCount)
                assertTrue(fakeBlacklist.blacklistedTokens.isEmpty())
        }

        @Test
        fun `logout - handles missing refresh token gracefully`() {
                val accessToken = "valid.access.token.no.refresh"
                val expirationDate = Date(System.currentTimeMillis() + 1000 * 600)

                `when`(jwtTokenProvider.validateToken(accessToken)).thenReturn(true)
                `when`(jwtTokenProvider.getExpiration(accessToken)).thenReturn(expirationDate)

                authService.logout(accessToken, null)

                assertTrue(fakeBlacklist.blacklistedTokens.containsKey(accessToken))
                // verify no delete was called by checking no interaction on findById
                verify(refreshTokenRepository, never()).findById(anyKotlin())
        }

        @Test
        fun `logout - strips Bearer prefix from refresh token`() {
                val accessToken = "valid.access.token.bearer"
                val rawRefreshToken = "raw.refresh.token"
                val refreshTokenWithBearer = "Bearer $rawRefreshToken"
                val username = "testuser"
                val expirationDate = Date(System.currentTimeMillis() + 1000 * 600)
                val savedRefreshToken = RefreshToken(refreshToken = rawRefreshToken, username = username)

                `when`(jwtTokenProvider.validateToken(accessToken)).thenReturn(true)
                `when`(jwtTokenProvider.getExpiration(accessToken)).thenReturn(expirationDate)
                `when`(refreshTokenRepository.findById(rawRefreshToken)).thenReturn(Optional.of(savedRefreshToken))

                authService.logout(accessToken, refreshTokenWithBearer)

                assertTrue(fakeBlacklist.blacklistedTokens.containsKey(accessToken))
                verify(refreshTokenRepository).delete(savedRefreshToken)
        }

        @Test
        fun `FakeTokenBlacklistService correctly records and checks blacklisted tokens`() {
                val fake = FakeTokenBlacklistService()

                assertFalse(fake.isBlacklisted("not.blacklisted"))
                fake.blacklist("revoked.token", 300L)
                assertTrue(fake.isBlacklisted("revoked.token"))
                assertFalse(fake.isBlacklisted("other.token"))
                assertEquals(1L, fake.blacklistSize())
        }
}
