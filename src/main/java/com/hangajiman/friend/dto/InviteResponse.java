package com.hangajiman.friend.dto;

import java.time.OffsetDateTime;

/**
 * POST /friends/invites → 201 (API 명세서 🤝 Friend & Cheer)
 */
public record InviteResponse(String inviteCode, String inviteUrl, OffsetDateTime expiresAt) {
}
