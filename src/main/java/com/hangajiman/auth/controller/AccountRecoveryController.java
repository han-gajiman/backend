package com.hangajiman.auth.controller;

import java.time.Duration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.auth.dto.AuthDtos.FindEmailRequest;
import com.hangajiman.auth.dto.AuthDtos.FindEmailResponse;
import com.hangajiman.auth.dto.AuthDtos.ResetCodeRequest;
import com.hangajiman.auth.dto.AuthDtos.ResetPasswordRequest;
import com.hangajiman.auth.service.AccountRecoveryService;
import com.hangajiman.auth.service.RequestLimiter;
import com.hangajiman.global.response.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// 자체 로그인 계정 찾기 (로그인 전 🔓). 같은 IP·이메일에서 짧은 시간에 여러 번 부르면 429
@RestController
@RequestMapping("/api/v1/auth")
public class AccountRecoveryController {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final AccountRecoveryService recoveryService;
    private final RequestLimiter limiter;

    public AccountRecoveryController(AccountRecoveryService recoveryService, RequestLimiter limiter) {
        this.recoveryService = recoveryService;
        this.limiter = limiter;
    }

    // POST /auth/find-email 🔓 닉네임 → 가린 이메일 목록 (없으면 빈 목록)
    @PostMapping("/find-email")
    public ApiResponse<FindEmailResponse> findEmail(@Valid @RequestBody FindEmailRequest request,
                                                    HttpServletRequest http) {
        limiter.check("find-email:" + http.getRemoteAddr(), 10, WINDOW);
        return ApiResponse.ok(new FindEmailResponse(recoveryService.findEmails(request.nickname())));
    }

    // POST /auth/password/reset-code 🔓 → 204 (가입 여부와 관계없이 같은 응답)
    @PostMapping("/password/reset-code")
    public ResponseEntity<Void> sendResetCode(@Valid @RequestBody ResetCodeRequest request, HttpServletRequest http) {
        limiter.check("reset-code:" + http.getRemoteAddr(), 10, WINDOW);
        limiter.check("reset-code:" + request.email().trim().toLowerCase(), 5, Duration.ofHours(1));
        recoveryService.sendResetCode(request.email());
        return ResponseEntity.noContent().build();
    }

    // POST /auth/password/reset 🔓 → 204. 코드가 틀리거나 만료되면 400 AUTH_RESET_CODE_INVALID
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        limiter.check("reset:" + http.getRemoteAddr(), 20, WINDOW);
        recoveryService.resetPassword(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
