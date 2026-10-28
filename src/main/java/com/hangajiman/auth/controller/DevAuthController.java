package com.hangajiman.auth.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.auth.dto.AuthDtos.LoginResponse;
import com.hangajiman.auth.service.AuthService;
import com.hangajiman.global.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 로컬 개발 전용 로그인. local 프로필에서만 등록되어 배포 환경에는 없습니다.
 * 앱에서 카카오·Google SDK 없이 실제 서버에 로그인(자동 가입 + Access/Refresh Token)할 때 씁니다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
public class DevAuthController {

    private final AuthService authService;

    public DevAuthController(AuthService authService) {
        this.authService = authService;
    }

    public record DevLoginRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String devKey) {
    }

    // POST /api/v1/dev/login { "devKey": "me" } 🔓 — 응답은 POST /auth/kakao와 같습니다.
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody DevLoginRequest request) {
        return ApiResponse.ok(authService.loginForDev(request.devKey()));
    }
}
