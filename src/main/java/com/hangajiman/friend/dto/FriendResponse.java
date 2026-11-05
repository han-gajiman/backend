package com.hangajiman.friend.dto;

/**
 * GET /friends 📄 항목. 친구의 진행 중 돌탑이 없거나 비공개(PRIVATE)면 wish는 null
 */
public record FriendResponse(Long friendId, String nickname, WishSummary wish) {

    // spotId·spotVisited: 친구 돌탑의 명소 배경용 (방문 전이면 일러스트를 흐리게)
    public record WishSummary(Long wishId, String content, long stoneCount, boolean stonedToday,
                              Long spotId, boolean spotVisited) {
    }
}
