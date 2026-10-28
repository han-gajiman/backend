package com.hangajiman.friend.dto;

/**
 * POST /friends/invites/{inviteCode}/accept → 200. 초대한 사람(새 친구) 정보
 */
public record InviteAcceptResponse(Long friendId, String nickname) {
}
