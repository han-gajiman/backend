package com.hangajiman.wish.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.ai.dto.SuggestionDtos.SuggestionRequest;
import com.hangajiman.ai.dto.SuggestionDtos.SuggestionResponse;
import com.hangajiman.ai.service.WishSuggestionService;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.wish.dto.WishDtos.CreateWishRequest;
import com.hangajiman.wish.dto.WishDtos.CreateWishResponse;
import com.hangajiman.wish.dto.WishDtos.CurrentWishResponse;
import com.hangajiman.wish.dto.WishDtos.ReflectionRequest;
import com.hangajiman.wish.dto.WishDtos.RoundSummary;
import com.hangajiman.wish.dto.WishDtos.VisibilityRequest;
import com.hangajiman.wish.dto.WishDtos.WishSummary;
import com.hangajiman.wish.dto.WishDtos.YearReviewRequest;
import com.hangajiman.wish.dto.WishDtos.YearReviewResponse;
import com.hangajiman.wish.service.WishService;
import com.hangajiman.wish.service.YearReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wishes")
public class WishController {

    private final WishService wishService;
    private final YearReviewService yearReviewService;
    private final WishSuggestionService wishSuggestionService;

    public WishController(WishService wishService, YearReviewService yearReviewService,
                          WishSuggestionService wishSuggestionService) {
        this.wishService = wishService;
        this.yearReviewService = yearReviewService;
        this.wishSuggestionService = wishSuggestionService;
    }

    // WISH-03 POST /wishes → 201
    @PostMapping
    public ResponseEntity<ApiResponse<CreateWishResponse>> create(@AuthenticationPrincipal Long userId,
                                                                  @Valid @RequestBody CreateWishRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(wishService.create(userId, request)));
    }

    // GET /wishes/current — 없으면 data: null
    @GetMapping("/current")
    public ApiResponse<CurrentWishResponse> getCurrent(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(wishService.getCurrent(userId));
    }

    // WISH-04 PATCH /wishes/{wishId}/visibility → 204
    @PatchMapping("/{wishId}/visibility")
    public ResponseEntity<Void> changeVisibility(@AuthenticationPrincipal Long userId, @PathVariable Long wishId,
                                                 @Valid @RequestBody VisibilityRequest request) {
        wishService.changeVisibility(userId, wishId, request.visibility());
        return ResponseEntity.noContent().build();
    }

    // TOWER-02 POST /wishes/{wishId}/reflection → 204 (COMPLETED → CLOSED)
    @PostMapping("/{wishId}/reflection")
    public ResponseEntity<Void> reflect(@AuthenticationPrincipal Long userId, @PathVariable Long wishId,
                                        @Valid @RequestBody ReflectionRequest request) {
        wishService.reflect(userId, wishId, request);
        return ResponseEntity.noContent().build();
    }

    // TOWER-03 GET /wishes?round=1 — round 생략 시 현재 회차 (최대 12개라 페이지 없음)
    @GetMapping
    public ApiResponse<List<WishSummary>> getCollection(@AuthenticationPrincipal Long userId,
                                                        @RequestParam(required = false) Integer round) {
        return ApiResponse.ok(wishService.getCollection(userId, round));
    }

    // TOWER-03 GET /wishes/rounds — 돌탑이 있는 회차 + 현재 회차, 1년 회고 포함
    @GetMapping("/rounds")
    public ApiResponse<List<RoundSummary>> getRounds(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(yearReviewService.getRounds(userId));
    }

    // TOWER-04 POST /wishes/rounds/{round}/review → 201. 끝난 회차에 돌탑이 있을 때 한 번만, 수정 불가
    @PostMapping("/rounds/{round}/review")
    public ResponseEntity<ApiResponse<YearReviewResponse>> writeYearReview(@AuthenticationPrincipal Long userId,
                                                                           @PathVariable int round,
                                                                           @Valid @RequestBody YearReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(yearReviewService.writeReview(userId, round, request.content())));
    }

    // WISH-02 POST /wishes/suggestions — AI 실천 목표 3개 + 명소 추천, 1인 하루 10회
    @PostMapping("/suggestions")
    public ApiResponse<SuggestionResponse> suggest(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody SuggestionRequest request) {
        return ApiResponse.ok(wishSuggestionService.suggest(userId, request.content()));
    }
}
