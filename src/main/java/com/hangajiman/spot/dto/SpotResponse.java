package com.hangajiman.spot.dto;

import com.hangajiman.spot.domain.Spot;

/**
 * GET /spots 응답 항목 (API 명세서 🗺️ Spot)
 */
public record SpotResponse(
        Long id,
        String name,
        String region,
        String category,
        String description,
        double latitude,
        double longitude,
        boolean visited // 내가 방문 인증한 명소인지. 로그인 전이면 false
) {

    public static SpotResponse from(Spot spot, boolean visited) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getRegion(),
                spot.getCategory(),
                spot.getDescription(),
                spot.getLatitude().doubleValue(),
                spot.getLongitude().doubleValue(),
                visited
        );
    }
}
