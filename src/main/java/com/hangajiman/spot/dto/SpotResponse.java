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
        double longitude
) {

    public static SpotResponse from(Spot spot) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getRegion(),
                spot.getCategory(),
                spot.getDescription(),
                spot.getLatitude().doubleValue(),
                spot.getLongitude().doubleValue()
        );
    }
}
