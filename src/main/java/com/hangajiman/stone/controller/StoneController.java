package com.hangajiman.stone.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.stone.dto.StoneDtos.CreateStoneRequest;
import com.hangajiman.stone.dto.StoneDtos.CreateStoneResponse;
import com.hangajiman.stone.dto.StoneDtos.StoneItem;
import com.hangajiman.plan.service.PlanService;
import com.hangajiman.stone.service.StoneService;

@RestController
@RequestMapping("/api/v1/wishes/{wishId}/stones")
public class StoneController {

    private final StoneService stoneService;

    private final PlanService planService;

    public StoneController(StoneService stoneService, PlanService planService) {
        this.stoneService = stoneService;
        this.planService = planService;
    }

    // STONE-01 POST /wishes/{wishId}/stones → 201. 본문을 생략하거나 content가 없으면 출석
    @PostMapping
    public ResponseEntity<ApiResponse<CreateStoneResponse>> stack(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long wishId,
            @Validated @RequestBody(required = false) CreateStoneRequest request) {
        String content = request == null ? null : request.content();
        CreateStoneResponse response = stoneService.stack(userId, wishId, content);
        planService.markDoneToday(userId, request == null ? null : request.doneTodoIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // STONE-01 GET /wishes/{wishId}/stones?page=0&size=20 📄 최신순
    @GetMapping
    public ApiResponse<PageResponse<StoneItem>> getStones(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long wishId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ApiResponse.ok(stoneService.getStones(userId, wishId, page, size));
    }
}
