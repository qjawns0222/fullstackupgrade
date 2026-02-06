package com.example.demo.service

import com.example.demo.dto.LoginRequest
import com.example.demo.dto.TokenDto
import com.example.demo.entity.RefreshToken
import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.repository.UserRepository
import com.example.demo.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
        private val authenticationManager: AuthenticationManager,
        private val jwtTokenProvider: JwtTokenProvider,
        private val refreshTokenRepository: RefreshTokenRepository,
        private val customUserDetailsService: CustomUserDetailsService,
        private val userRepository: UserRepository,
        private val mfaService: MfaService
) {

        @Transactional
        fun login(loginRequest: LoginRequest): TokenDto {
                // Authenticate first (Password check)
                val authenticationToken =
                        UsernamePasswordAuthenticationToken(
                                loginRequest.username,
                                loginRequest.password
                        )
                val authentication = authenticationManager.authenticate(authenticationToken)

                // Mfa Check
                val user =
                        userRepository.findByUsername(authentication.name).orElseThrow {
                                RuntimeException("User not found")
                        }

                if (user.mfaEnabled) {
                        // Return 'MFA_REQUIRED' signal.
                        // In a real world, we might issue a temporary JWT with limitation.
                        // For now, we return empty tokens and mfaRequired=true.
                        // Client should then call /auth/mfa/verify
                        return TokenDto(
                                grantType = "MFA_REQUIRED",
                                accessToken =
                                        "TEMP_TOKEN_WAITING_FOR_OTP", // Should be a short-lived
                                // temp token in prod
                                refreshToken = "",
                                accessTokenExpiresIn = 0,
                                mfaRequired = true
                        )
                }

                val tokenInfo = jwtTokenProvider.createToken(authentication)

                // Save Refresh Token
                refreshTokenRepository.save(
                        RefreshToken(
                                refreshToken = tokenInfo.refreshToken,
                                username = authentication.name
                        )
                )

                return TokenDto(
                        grantType = tokenInfo.grantType,
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken,
                        accessTokenExpiresIn = tokenInfo.accessTokenExpiresIn,
                        mfaRequired = false
                )
        }

        @Transactional
        fun verifyMfa(username: String, otp: String): TokenDto {
                val user =
                        userRepository.findByUsername(username).orElseThrow {
                                RuntimeException("User not found")
                        }

                if (!user.mfaEnabled) {
                        throw RuntimeException("MFA not enabled for this user")
                }

                if (!mfaService.verifyOtp(user.mfaSecret ?: "", otp)) {
                        throw RuntimeException("Invalid OTP Code")
                }

                // Success: Generate Tokens
                val userDetails = customUserDetailsService.loadUserByUsername(username)
                val authentication =
                        UsernamePasswordAuthenticationToken(
                                userDetails,
                                "",
                                userDetails.authorities
                        )

                val tokenInfo = jwtTokenProvider.createToken(authentication)

                refreshTokenRepository.save(
                        RefreshToken(
                                refreshToken = tokenInfo.refreshToken,
                                username = authentication.name
                        )
                )

                return TokenDto(
                        grantType = tokenInfo.grantType,
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken,
                        accessTokenExpiresIn = tokenInfo.accessTokenExpiresIn,
                        mfaRequired = false
                )
        }

        @Transactional
        fun reissue(refreshToken: String): TokenDto {
                // 1. Validate Token Signature
                if (!jwtTokenProvider.validateToken(refreshToken)) {
                        throw RuntimeException("Refresh Token이 유효하지 않습니다.")
                }

                // 2. Get Subject (Username) from Token (Logic fixed: No 'auth' claim check needed
                // here)
                val username = jwtTokenProvider.getSubject(refreshToken)

                // 3. Check consistency with Redis
                val savedRefreshToken =
                        refreshTokenRepository.findById(refreshToken).orElseThrow {
                                // Possible scenarios:
                                // A. Token expired and removed from Redis
                                // B. Token was already used (rotated) -> Reuse Attempt!
                                // Here we can treat it as "Logged out or Invalid" for now.
                                // To implement strict Reuse Detection, we'd need another store or
                                // Family ID.
                                RuntimeException("로그아웃 된 사용자이거나, 이미 만료/사용된 토큰입니다.")
                        }

                if (savedRefreshToken.username != username) {
                        throw RuntimeException("토큰의 유저 정보가 일치하지 않습니다.")
                }

                // 4. Load fresh UserDetails to enforce current roles/state
                val userDetails = customUserDetailsService.loadUserByUsername(username)
                val authentication =
                        UsernamePasswordAuthenticationToken(
                                userDetails,
                                "",
                                userDetails.authorities
                        )

                // 5. Generate New Tokens (Rotate)
                val tokenInfo = jwtTokenProvider.createToken(authentication)

                // 6. Delete old token (Invalidate)
                refreshTokenRepository.delete(savedRefreshToken)

                // 7. Save new token
                refreshTokenRepository.save(
                        RefreshToken(refreshToken = tokenInfo.refreshToken, username = username)
                )

                return TokenDto(
                        grantType = tokenInfo.grantType,
                        accessToken = tokenInfo.accessToken,
                        refreshToken = tokenInfo.refreshToken,
                        accessTokenExpiresIn = tokenInfo.accessTokenExpiresIn
                )
        }
}
