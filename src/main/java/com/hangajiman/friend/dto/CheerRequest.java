package com.hangajiman.friend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /wishes/{wishId}/cheers. 100자 초과는 서비스에서 400 CHEER_TOO_LONG
 */
public record CheerRequest(@NotBlank String message) {
}
