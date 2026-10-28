package com.hangajiman.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hangajiman.global.config.ClockConfig;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Access Token(JWT) 발급·검증. sub = userId.
 * 발급 시각·만료 판단은 실제 시계(systemClock) 기준입니다.
 * 시연용 시간 이동(DEMO-01)을 따라가면 이동하는 순간 모든 토큰이 만료되어 시연이 끊기기 때문입니다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessExpSeconds;
    private final Clock clock;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.access-exp-seconds}") long accessExpSeconds,
                       @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // 32바이트 미만이면 기동 실패
        this.accessExpSeconds = accessExpSeconds;
        this.clock = clock;
    }

    public String createAccessToken(Long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpSeconds)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료가 유효하면 userId, 아니면 null */
    public Long parseUserId(String token) {
        try {
            String sub = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(sub);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
