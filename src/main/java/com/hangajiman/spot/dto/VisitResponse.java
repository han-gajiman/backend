package com.hangajiman.spot.dto;

/**
 * POST /spots/{spotId}/visits 응답. firstVisit=false면 이미 인증한 명소입니다.
 */
public record VisitResponse(Long visitId, boolean firstVisit) {
}
