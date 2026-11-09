package com.hangajiman.postcard.controller;

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
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.postcard.dto.PostcardDtos.LetterResponse;
import com.hangajiman.postcard.dto.PostcardDtos.PostcardResponse;
import com.hangajiman.postcard.dto.PostcardDtos.SendLetterRequest;
import com.hangajiman.postcard.dto.PostcardDtos.SendLetterResponse;
import com.hangajiman.postcard.service.PostcardService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/postcards")
public class PostcardController {

    private final PostcardService postcardService;

    public PostcardController(PostcardService postcardService) {
        this.postcardService = postcardService;
    }

    // SPOT-04 GET /postcards — 내 엽서함 (명소당 4종, 적어서 페이지 없음)
    @GetMapping
    public ApiResponse<List<PostcardResponse>> getPostcards(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(postcardService.getPostcards(userId));
    }

    // SPOT-04 POST /postcards/letters → 201. 가진 엽서에 글을 써서 친구 또는 나에게
    @PostMapping("/letters")
    public ResponseEntity<ApiResponse<SendLetterResponse>> send(@AuthenticationPrincipal Long userId,
                                                                @Valid @RequestBody SendLetterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(postcardService.send(userId, request)));
    }

    // SPOT-04 GET /postcards/letters/received?page=0&size=20 📄 받은 엽서, 최신순
    @GetMapping("/letters/received")
    public ApiResponse<PageResponse<LetterResponse>> getReceived(@AuthenticationPrincipal Long userId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ApiResponse.ok(postcardService.getReceived(userId, page, size));
    }

    // SPOT-04 GET /postcards/letters/sent?page=0&size=20 📄 내가 보낸 엽서, 최신순 (나에게 보낸 것 포함)
    @GetMapping("/letters/sent")
    public ApiResponse<PageResponse<LetterResponse>> getSent(@AuthenticationPrincipal Long userId,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ApiResponse.ok(postcardService.getSent(userId, page, size));
    }

    // SPOT-04 PATCH /postcards/letters/{id}/read → 204
    @PatchMapping("/letters/{letterId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Long userId, @PathVariable Long letterId) {
        postcardService.markRead(userId, letterId);
        return ResponseEntity.noContent().build();
    }
}
