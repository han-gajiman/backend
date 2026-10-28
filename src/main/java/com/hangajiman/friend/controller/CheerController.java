package com.hangajiman.friend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.friend.dto.CheerCreateResponse;
import com.hangajiman.friend.dto.CheerRequest;
import com.hangajiman.friend.dto.CheerResponse;
import com.hangajiman.friend.service.CheerService;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.global.response.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/wishes/{wishId}/cheers")
public class CheerController {

    private final CheerService cheerService;

    public CheerController(CheerService cheerService) {
        this.cheerService = cheerService;
    }

    // FRIEND-03 POST /wishes/{wishId}/cheers → 201
    @PostMapping
    public ResponseEntity<ApiResponse<CheerCreateResponse>> writeCheer(@AuthenticationPrincipal Long userId,
                                                                       @PathVariable Long wishId,
                                                                       @Valid @RequestBody CheerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cheerService.writeCheer(userId, wishId, request.message())));
    }

    // FRIEND-03 GET /wishes/{wishId}/cheers?page=0&size=20 📄
    @GetMapping
    public ApiResponse<PageResponse<CheerResponse>> getCheers(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long wishId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(cheerService.getCheers(userId, wishId, page, size));
    }
}
