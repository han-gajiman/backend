package com.hangajiman.auth.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    // 웹 카카오 로그인: 카카오 로그인 화면에서 돌아올 때 받은 인가 코드와, 그때 쓴 돌아올 주소
    public record KakaoCodeLoginRequest(@NotBlank String code, @NotBlank @Size(max = 300) String redirectUri) {
    }

    public record KakaoAuthorizeUrlResponse(String url) {
    }

    public record GoogleLoginRequest(@NotBlank String idToken) {
    }

    // POST /auth/signup/local — 자체 회원가입 요청
    public record LocalSignUpRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 30, message = "닉네임은 최대 30자까지 가능합니다.")
        String nickname
    ) {
    }

    // POST /auth/login/local — 자체 로그인 요청
    public record LocalLoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
    ) {
    }

    // POST /auth/find-email — 아이디(이메일) 찾기
    public record FindEmailRequest(@NotBlank @Size(max = 30) String nickname) {
    }

    public record FindEmailResponse(List<String> emails) {
    }

    // POST /auth/password/reset-code — 비밀번호 찾기 인증코드 보내기
    public record ResetCodeRequest(@NotBlank @Email String email) {
    }

    // POST /auth/password/reset — 인증코드 확인 후 새 비밀번호
    public record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증코드는 숫자 6자리예요.") String code,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword
    ) {
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
