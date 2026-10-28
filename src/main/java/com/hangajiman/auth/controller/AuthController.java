package com.hangajiman.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.auth.dto.AuthDtos.GoogleLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.KakaoLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.LoginResponse;
import com.hangajiman.auth.dto.AuthDtos.RefreshTokenRequest;
import com.hangajiman.auth.dto.AuthDtos.TokenResponse;
import com.hangajiman.auth.service.AuthService;
import com.hangajiman.global.response.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // POST /auth/kakao 🔓
    @PostMapping("/kakao")
    public ApiResponse<LoginResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        return ApiResponse.ok(authService.loginWithKakao(request.accessToken()));
    }

    // POST /auth/google 🔓
    @PostMapping("/google")
    public ApiResponse<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.loginWithGoogle(request.idToken()));
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
