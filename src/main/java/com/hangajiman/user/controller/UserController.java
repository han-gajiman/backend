package com.hangajiman.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.auth.service.AuthService;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.user.dto.MeResponse;
import com.hangajiman.user.dto.NicknameRequest;
import com.hangajiman.user.dto.OnboardingRequest;
import com.hangajiman.user.dto.PushTokenRequest;
import com.hangajiman.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    public UserController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    // AUTH-02 POST /users/me/onboarding → 204
    @PostMapping("/onboarding")
    public ResponseEntity<Void> onboard(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody OnboardingRequest request) {
        userService.onboard(userId, request);
        return ResponseEntity.noContent().build();
    }

    // AUTH-04 GET /users/me
    @GetMapping
    public ApiResponse<MeResponse> getMe(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.getMe(userId));
    }

    // AUTH-06 DELETE /users/me → 204 (즉시 삭제)
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Long userId) {
        authService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }

    // AUTH-04 PUT /users/me/push-token → 204
    // AUTH-04 PATCH /users/me/nickname → 204, 겹치면 409 NICKNAME_DUPLICATED
    @PatchMapping("/nickname")
    public ResponseEntity<Void> changeNickname(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody NicknameRequest request) {
        userService.changeNickname(userId, request.nickname());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/push-token")
    public ResponseEntity<Void> updatePushToken(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody PushTokenRequest request) {
        userService.updatePushToken(userId, request.pushToken());
        return ResponseEntity.noContent().build();
    }
}
