package com.hangajiman.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 인증 API 요청·응답 (API 명세서 🔐 Auth)
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // POST /auth/kakao — 앱의 카카오 SDK에서 받은 액세스 토큰
    public record KakaoLoginRequest(@NotBlank String accessToken) {
    }

    // POST /auth/google — 앱의 Google 로그인에서 받은 ID 토큰
    public record GoogleLoginRequest(@NotBlank String idToken) {
    }

    // POST /auth/refresh, /auth/logout
    public record RefreshTokenRequest(@NotBlank String refreshToken) {
    }

    // onboarded=false면 프론트는 온보딩 화면으로
    public record LoginResponse(String accessToken, String refreshToken, boolean isNewUser, boolean onboarded) {
    }

    public record TokenResponse(String accessToken, String refreshToken) {
    }
}
