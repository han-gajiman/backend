package com.hangajiman.global.security;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;

/**
 * 로컬 개발 전용. 소셜 로그인 없이 Access Token을 받아 다른 API를 테스트할 때 씁니다.
 * local 프로필에서만 등록되므로 배포 환경에는 존재하지 않습니다.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
public class DevTokenController {

    private final JwtProvider jwtProvider;

    public DevTokenController(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // GET /api/v1/dev/token?userId=1  🔓
    @GetMapping("/token")
    public ApiResponse<Map<String, String>> token(@RequestParam Long userId) {
        return ApiResponse.ok(Map.of("accessToken", jwtProvider.createAccessToken(userId)));
    }

    // GET /api/v1/dev/me  (Authorization: Bearer {accessToken})
    @GetMapping("/me")
    public ApiResponse<Map<String, Long>> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(Map.of("userId", userId));
    }
}
