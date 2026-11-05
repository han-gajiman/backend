package com.hangajiman.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /users/me/onboarding. TERMS·PRIVACY 필수
 */
public record OnboardingRequest(
        @NotBlank @Size(max = 30) String nickname,
        @NotNull @Valid Agreements agreements,
        @NotBlank @Size(max = 10) String agreementVersion
) {

    public record Agreements(boolean terms, boolean privacy) {
    }
}
