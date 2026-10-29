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

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.wish.dto.WishDtos.CreateWishRequest;
import com.hangajiman.wish.dto.WishDtos.CreateWishResponse;
import com.hangajiman.wish.dto.WishDtos.CurrentWishResponse;
import com.hangajiman.wish.dto.WishDtos.ReflectionRequest;
import com.hangajiman.wish.dto.WishDtos.VisibilityRequest;
import com.hangajiman.wish.dto.WishDtos.WishSummary;
import com.hangajiman.wish.service.WishService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wishes")
public class WishController {

    private final WishService wishService;

    public WishController(WishService wishService) {
        this.wishService = wishService;
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
}
