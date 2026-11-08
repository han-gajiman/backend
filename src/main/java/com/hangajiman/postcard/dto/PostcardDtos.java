package com.hangajiman.postcard.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 엽서 API 요청·응답 (API 명세서 🗺️ Spot & Postcard)
 */
public final class PostcardDtos {

    private PostcardDtos() {
    }

    // GET /postcards 항목, 방문 인증 응답의 새 엽서
    public record PostcardResponse(Long spotId, String spotName, int style, Instant issuedAt) {
    }

    // POST /postcards/letters. receiverId가 없거나 내 id면 나에게 보내기
    public record SendLetterRequest(
            @NotNull Long spotId,
            @NotNull @Min(1) @Max(4) Integer style,
            Long receiverId,
            @NotBlank @Size(max = 100) String message
    ) {
    }

    public record SendLetterResponse(Long letterId) {
    }

    // GET /postcards/letters/received 항목. TO = 받는 사람(나), FROM = 보낸 사람
    public record LetterResponse(
            Long letterId,
            Long spotId,
            String spotName,
            int style,
            String message,
            String fromNickname,
            String toNickname,
            boolean fromMe,
            boolean read,
            Instant sentAt
    ) {
    }
}
