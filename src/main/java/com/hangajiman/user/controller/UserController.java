package com.hangajiman.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.user.dto.LocationAgreementRequest;
import com.hangajiman.user.dto.MeResponse;
import com.hangajiman.user.dto.OnboardingRequest;
import com.hangajiman.user.dto.PushTokenRequest;
import com.hangajiman.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    // AUTH-04 PUT /users/me/push-token → 204
    @PutMapping("/push-token")
    public ResponseEntity<Void> updatePushToken(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody PushTokenRequest request) {
        userService.updatePushToken(userId, request.pushToken());
        return ResponseEntity.noContent().build();
    }

    // AUTH-05 PUT /users/me/agreements/location → 204
    @PutMapping("/agreements/location")
    public ResponseEntity<Void> updateLocationAgreement(@AuthenticationPrincipal Long userId,
                                                        @Valid @RequestBody LocationAgreementRequest request) {
        userService.updateLocationAgreement(userId, request.agreed());
        return ResponseEntity.noContent().build();
    }
}
