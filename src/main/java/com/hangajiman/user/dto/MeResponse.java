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
        boolean hasActiveWish
) {
}
