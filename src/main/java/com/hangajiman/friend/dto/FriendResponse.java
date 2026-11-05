package com.hangajiman.friend.dto;

/**
 * GET /friends 📄 항목. 친구의 진행 중 돌탑이 없거나 비공개(PRIVATE)면 wish는 null
 */
public record FriendResponse(Long friendId, String nickname, WishSummary wish) {

    // spotId·spotName·spotVisited: 친구 돌탑 화면의 명소 배경과 방문 여부 표시용
    public record WishSummary(Long wishId, String content, long stoneCount, boolean stonedToday,
                              Long spotId, String spotName, boolean spotVisited) {
    }
}
