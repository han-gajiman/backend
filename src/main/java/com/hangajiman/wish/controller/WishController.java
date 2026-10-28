package com.hangajiman.wish.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.wish.dto.WishDtos.CreateWishRequest;
import com.hangajiman.wish.dto.WishDtos.CreateWishResponse;
import com.hangajiman.wish.dto.WishDtos.CurrentWishResponse;
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
}
