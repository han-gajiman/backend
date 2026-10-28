package com.hangajiman.demo;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.wish.service.TowerCompletionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DEMO-01 시연용 시간 이동. local(개발)·demo(시연) 프로필에서만 등록되어 운영 서버에는 없습니다.
 * 서버 시계를 N일 앞당기고 30일 완성 처리를 즉시 실행합니다. 서버 전체에 적용되며, 재시작하면 원래 시간으로 돌아옵니다.
 */
@Profile({"local", "demo"})
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoClock clock;
    private final TowerCompletionService towerCompletionService;

    public DemoController(DemoClock clock, TowerCompletionService towerCompletionService) {
        this.clock = clock;
        this.towerCompletionService = towerCompletionService;
    }

    public record TimeTravelRequest(@NotNull @Min(1) @Max(365) Integer days) {
    }

    public record TimeTravelResponse(OffsetDateTime serverNow) {
    }

    // POST /demo/time-travel { "days": 30 }
    @PostMapping("/time-travel")
    public ApiResponse<TimeTravelResponse> timeTravel(@Valid @RequestBody TimeTravelRequest request) {
        clock.travel(Duration.ofDays(request.days()));
        towerCompletionService.completeExpired();
        return ApiResponse.ok(new TimeTravelResponse(OffsetDateTime.now(clock)));
    }
}
