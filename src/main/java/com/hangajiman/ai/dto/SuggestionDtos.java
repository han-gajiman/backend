package com.hangajiman.ai.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /wishes/suggestions 요청·응답 (API 명세서 🌠 Wish, WISH-02)
 */
public final class SuggestionDtos {

    private SuggestionDtos() {
    }

    public record SuggestionRequest(@NotBlank @Size(max = 100) String content) {
    }

    // spot: AI가 실패했거나 위험한 소원이면 null. fallback: 기본 문구를 썼는지
    // supportMessage: 위험한 소원이면 도움 안내 (그때 actionGoals는 비어 있음)
    public record SuggestionResponse(List<String> actionGoals, SpotPick spot, boolean fallback, String supportMessage) {
    }

    public record SpotPick(Long spotId, String spotName, String reason) {
    }
}
