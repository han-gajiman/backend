package com.hangajiman.spot.controller;

import java.util.List;

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

    // GET /spots?category=PASS 🔓 인증 불필요
    @GetMapping
    public ApiResponse<List<SpotResponse>> getSpots(@RequestParam(required = false) String category) {
        return ApiResponse.ok(spotService.getSpots(category));
    }
}
