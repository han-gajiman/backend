package com.hangajiman.demo;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.service.TowerCompletionService;
import com.hangajiman.wish.service.YearReviewService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * DEMO-01 시연용 시간 이동. local(개발)·demo(시연) 프로필에서만 등록되어 운영 서버에는 없습니다.
 * N일 앞당기고 30일 완성 처리·1년 회고 알림을 즉시 실행합니다. 재시작하면 원래 시간으로 돌아옵니다.
 * - 로컬(허용 계정 목록 없음): 서버 전체가 함께 이동
 * - 실서버(demo.time-travel.user-emails, 11/9): 허용된 계정들만 한 묶음으로 함께 이동(11/10), 다른 계정은 403 DEMO_NOT_ALLOWED
 */
@Profile({"local", "demo"})
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoClock clock;
    private final TowerCompletionService towerCompletionService;
    private final YearReviewService yearReviewService;
    private final TimeTravelPolicy policy;
    private final UserRepository userRepository;
    private final DemoScenarioService scenarioService;

    public DemoController(DemoClock clock, TowerCompletionService towerCompletionService,
                          YearReviewService yearReviewService, TimeTravelPolicy policy, UserRepository userRepository,
                          DemoScenarioService scenarioService) {
        this.clock = clock;
        this.towerCompletionService = towerCompletionService;
        this.yearReviewService = yearReviewService;
        this.policy = policy;
        this.userRepository = userRepository;
        this.scenarioService = scenarioService;
    }

    // DEMO-03 POST /demo/reset-scenario → 204. 시연 계정을 처음 세팅한 상태로 (시연 계정 본인만, 아니면 403 DEMO_NOT_ALLOWED)
    @PostMapping("/reset-scenario")
    public ResponseEntity<Void> resetScenario(@AuthenticationPrincipal Long userId) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || !scenarioService.canReset(user)) {
            throw new BusinessException(ErrorCode.DEMO_NOT_ALLOWED);
        }
        scenarioService.reset(user);
        return ResponseEntity.noContent().build();
    }

    public record TimeTravelRequest(@NotNull @Min(1) @Max(365) Integer days) {
    }

    public record TimeTravelResponse(OffsetDateTime serverNow) {
    }

    // POST /demo/time-travel { "days": 30 }
    @PostMapping("/time-travel")
    public ApiResponse<TimeTravelResponse> timeTravel(@AuthenticationPrincipal Long userId,
                                                      @Valid @RequestBody TimeTravelRequest request) {
        Duration days = Duration.ofDays(request.days());
        if (policy.perUser()) {
            requireAllowed(userId);
            // 묶음(허용 계정 전부)이 같은 날짜로 함께 이동. 이 요청의 시계도 바로 새 날짜
            List<Long> group = groupIds();
            group.forEach(id -> clock.travelUser(id, days));
            group.forEach(id -> {
                towerCompletionService.completeExpiredFor(id);
                yearReviewService.notifyDueFor(id);
            });
        } else {
            clock.travel(days);
            towerCompletionService.completeExpired();
            yearReviewService.notifyDue();
        }
        return ApiResponse.ok(new TimeTravelResponse(OffsetDateTime.now(clock)));
    }

    // POST /demo/time-travel/reset → 실제 날짜로 (실서버는 그 계정만)
    @PostMapping("/time-travel/reset")
    public ApiResponse<TimeTravelResponse> reset(@AuthenticationPrincipal Long userId) {
        if (policy.perUser()) {
            requireAllowed(userId);
            groupIds().forEach(clock::resetUser);
        } else {
            clock.reset();
        }
        return ApiResponse.ok(new TimeTravelResponse(OffsetDateTime.now(clock)));
    }

    // 시간 이동을 함께 하는 계정들 (허용 목록의 가입한 계정)
    private List<Long> groupIds() {
        return userRepository.findByEmailIn(policy.allowedEmails()).stream().map(User::getId).toList();
    }

    private void requireAllowed(Long userId) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || !policy.canTravel(user)) {
            throw new BusinessException(ErrorCode.DEMO_NOT_ALLOWED);
        }
    }
}
