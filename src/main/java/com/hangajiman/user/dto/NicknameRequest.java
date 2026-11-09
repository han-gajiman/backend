package com.hangajiman.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// AUTH-04 PATCH /users/me/nickname
public record NicknameRequest(@NotBlank @Size(max = 30) String nickname) {
}
