package com.hangajiman.user.dto;

import java.time.LocalDate;

import com.hangajiman.user.domain.SocialProvider;

/**
 * GET /users/me (API 명세서 🔐 Auth · 회원)
 */
public record MeResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        SocialProvider provider,
        Agreements agreements,
        boolean hasActiveWish,
        Journey journey // 첫 돌탑 전이면 null
) {

    public record Agreements(boolean location) {
    }

    public record Journey(LocalDate startedOn, int currentRound, LocalDate reviewAt) {
    }
}
