package com.hangajiman.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /users/me/agreements/location
 */
public record LocationAgreementRequest(@NotNull Boolean agreed) {
}
