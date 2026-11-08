package com.hangajiman.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.auth.dto.AuthDtos.GoogleLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.KakaoAuthorizeUrlResponse;
import com.hangajiman.auth.dto.AuthDtos.KakaoCodeLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.KakaoLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.LocalLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.LocalSignUpRequest;
import com.hangajiman.auth.dto.AuthDtos.LoginResponse;
import com.hangajiman.auth.dto.AuthDtos.RefreshTokenRequest;
import com.hangajiman.auth.dto.AuthDtos.TokenResponse;
import com.hangajiman.auth.service.AuthService;
import com.hangajiman.auth.social.KakaoWebLoginClient;
import com.hangajiman.global.response.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final KakaoWebLoginClient kakaoWebLoginClient;

    public AuthController(AuthService authService, KakaoWebLoginClient kakaoWebLoginClient) {
        this.authService = authService;
        this.kakaoWebLoginClient = kakaoWebLoginClient;
    }

    // POST /auth/kakao 🔓
    @PostMapping("/kakao")
    public ApiResponse<LoginResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(authService.loginWithKakao(request.accessToken()));
    }

    // GET /auth/kakao/web/authorize-url 🔓 웹 카카오 로그인 화면 주소 (REST API 키는 서버에만)
    @GetMapping("/kakao/web/authorize-url")
    public ApiResponse<KakaoAuthorizeUrlResponse> kakaoAuthorizeUrl(@RequestParam String redirectUri,
                                                                    @RequestParam String state) {
        return ApiResponse.ok(new KakaoAuthorizeUrlResponse(kakaoWebLoginClient.authorizeUrl(redirectUri, state)));
    }

    // POST /auth/kakao/web/code 🔓 웹 카카오 로그인: 인가 코드 → 카카오 토큰 → 앱과 같은 카카오 로그인
    @PostMapping("/kakao/web/code")
    public ApiResponse<LoginResponse> loginWithKakaoCode(@Valid @RequestBody KakaoCodeLoginRequest request) {
        String accessToken = kakaoWebLoginClient.exchangeCode(request.code(), request.redirectUri());
        return ApiResponse.ok(authService.loginWithKakao(accessToken));
    }

    // POST /auth/google 🔓
    @PostMapping("/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.loginWithGoogle(request.idToken()));
    }

    // POST /auth/signup/local 🔓 자체 회원가입 (이메일·비밀번호·닉네임) → 바로 로그인 토큰
    @PostMapping("/signup/local")
    public ApiResponse<LoginResponse> signupLocal(@Valid @RequestBody LocalSignUpRequest request) {
        return ApiResponse.ok(authService.signupLocal(request));
    }

    // POST /auth/login/local 🔓 자체 로그인
    @PostMapping("/login/local")
    public ApiResponse<LoginResponse> loginLocal(@Valid @RequestBody LocalLoginRequest request) {
        return ApiResponse.ok(authService.loginLocal(request));
    }

    // POST /auth/refresh 🔓
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    // POST /auth/logout → 204
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(userId, request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
