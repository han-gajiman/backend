package com.hangajiman.spot.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.spot.dto.SpotResponse;
import com.hangajiman.spot.dto.VisitResponse;
import com.hangajiman.spot.service.SpotService;
import com.hangajiman.spot.service.SpotVisitService;

@RestController
@RequestMapping("/api/v1/spots")
public class SpotController {

    private final SpotService spotService;
    private final SpotVisitService spotVisitService;

    public SpotController(SpotService spotService, SpotVisitService spotVisitService) {
        this.spotService = spotService;
        this.spotVisitService = spotVisitService;
    }

    // GET /spots?category=PASS 🔓 인증 불필요. 토큰이 있으면 내가 방문한 명소를 visited로 표시
    @GetMapping
    public ApiResponse<List<SpotResponse>> getSpots(@RequestParam(required = false) String category,
                                                    @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(spotService.getSpots(category, userId));
    }

    // SPOT-03 POST /spots/{spotId}/visits → 201. 본문 없음: 앱이 기기 안에서 반경을 확인하고 결과만 보냅니다. 위치 정보는 받지 않습니다.
    @PostMapping("/{spotId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VisitResponse> visit(@AuthenticationPrincipal Long userId, @PathVariable Long spotId) {
        return ApiResponse.ok(spotVisitService.visit(userId, spotId));
    }
}
