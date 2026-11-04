package com.hangajiman.spot.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.spot.dto.SpotResponse;
import com.hangajiman.spot.service.SpotService;

@RestController
@RequestMapping("/api/v1/spots")
public class SpotController {

    private final SpotService spotService;

    public SpotController(SpotService spotService) {
        this.spotService = spotService;
    }

    // GET /spots?category=PASS 🔓 인증 불필요. 토큰이 있으면 내가 방문한 명소를 visited로 표시
    @GetMapping
    public ApiResponse<List<SpotResponse>> getSpots(@RequestParam(required = false) String category,
                                                    @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(spotService.getSpots(category, userId));
    }
}
