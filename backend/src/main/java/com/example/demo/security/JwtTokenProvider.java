package com.example.demo.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class JwtTokenProvider {

    // 토큰 유효성 검증 (Mock)
    public boolean validateToken(String token) {
        // 실제로는 JWT 서명 검증 로직이 들어감
        return "valid_token".equals(token);
    }

    // 토큰에서 인증 정보 조회 (Mock)
    public Authentication getAuthentication(String token) {
        // 실제로는 토큰 parsing 하여 userDetails 생성
        UserDetails userDetails = new User("user", "", Collections.emptyList());
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}
