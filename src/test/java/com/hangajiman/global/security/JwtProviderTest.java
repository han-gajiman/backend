package com.hangajiman.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-at-least-32-bytes!!";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final JwtProvider provider = new JwtProvider(SECRET, 1800, FIXED);

    @Test
    void issuedTokenReturnsUserId() {
        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = provider.createAccessToken(42L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThat(provider.parseUserId(tampered)).isNull();
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() {
        JwtProvider other = new JwtProvider("another-secret-key-for-jwt-at-least-32b!!", 1800, FIXED);

        assertThat(provider.parseUserId(other.createAccessToken(42L))).isNull();
    }

    @Test
    void expiredTokenIsRejectedByClock() {
        String token = provider.createAccessToken(42L);
        // 시연용 시간 이동처럼 Clock을 31분 뒤로 옮긴 Provider로 검증
        JwtProvider later = new JwtProvider(SECRET, 1800, Clock.offset(FIXED, Duration.ofMinutes(31)));

        assertThat(later.parseUserId(token)).isNull();
    }

    @Test
    void garbageIsRejected() {
        assertThat(provider.parseUserId("not-a-jwt")).isNull();
        assertThat(provider.parseUserId("")).isNull();
    }
}
