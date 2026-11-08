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
        int radiusM, // 방문 인증 반경. 앱이 기기 안에서 거리를 계산할 때 씁니다.
        boolean visited, // 내가 방문 인증한 명소인지. 로그인 전이면 false
        boolean visitedThisTower // 지금 돌탑(진행 중 소원) 기간에 인증했는지. false면 다시 인증할 수 있음
) {

    public static SpotResponse from(Spot spot, boolean visited, boolean visitedThisTower) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getRegion(),
                spot.getCategory(),
                spot.getDescription(),
                spot.getLatitude().doubleValue(),
                spot.getLongitude().doubleValue(),
                spot.getRadiusM(),
                visited,
                visitedThisTower
        );
    }
}
