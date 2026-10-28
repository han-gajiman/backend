package com.hangajiman.friend.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /wishes/{wishId}/cheers 📄 항목. 익명 응원이면 writerNickname은 null
 */
public record CheerResponse(
        Long cheerId,
        String writerNickname,
        @JsonProperty("isAnonymous") boolean anonymous,
        String message,
        OffsetDateTime createdAt
) {
}
