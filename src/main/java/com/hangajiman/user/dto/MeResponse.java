package com.hangajiman.user.dto;

import com.hangajiman.user.domain.SocialProvider;

/**
 * GET /users/me (API 명세서 🔐 Auth · 회원)
 */
public record MeResponse(
        Long id,
        String nickname,
        String profileImageUrl,
        SocialProvider provider,
        boolean hasActiveWish,
        boolean hasUnreadLetter, // 안 읽은 받은 엽서 (메인 우체통 깃발)
        boolean canTimeTravel, // 시연용 시간 이동 버튼을 보여줄지 (DEMO-01, 허용된 시연 계정만)
        boolean canResetDemo, // 내 정보의 "시연 처음 상태로" 버튼 (DEMO-03, 시연 계정 본인만)
        Stats stats // 내 정보 아래 기록 (11/10)
) {
    // towerCount: 완성한 돌탑 (30일이 끝난 것, 모아보기 기준), visitedSpotCount: 방문 인증한 명소 수, postcardCount: 모은 엽서
    public record Stats(long towerCount, long visitedSpotCount, long postcardCount) {
    }

}
