package com.hangajiman.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/me/push-token (예: ExponentPushToken[xxxx])
 */
public record PushTokenRequest(@NotBlank @Size(max = 200) String pushToken) {
}
