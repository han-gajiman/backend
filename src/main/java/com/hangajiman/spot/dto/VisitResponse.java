package com.hangajiman.spot.dto;

import com.hangajiman.postcard.dto.PostcardDtos.PostcardResponse;

/**
 * POST /spots/{spotId}/visits 응답. firstVisit=false면 지금 돌탑에서 이미 인증한 명소입니다.
 * postcard: 이번 인증으로 받은 엽서 (이미 인증했거나 4종을 다 모았으면 null)
 */
public record VisitResponse(Long visitId, boolean firstVisit, PostcardResponse postcard) {
}
