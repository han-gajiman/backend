package com.hangajiman.user.dto;

import com.hangajiman.user.domain.SocialProvider;

/**
 * GET /users/me (API 명세서 🔐 Auth · 회원)
 */
public record MeResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        SocialProvider provider,
        boolean hasActiveWish,
        boolean hasUnreadLetter // 안 읽은 받은 엽서 (메인 우체통 깃발)
) {
}
