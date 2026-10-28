package com.hangajiman.friend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.friend.dto.FriendResponse;
import com.hangajiman.friend.dto.InviteAcceptResponse;
import com.hangajiman.friend.dto.InviteResponse;
import com.hangajiman.friend.service.FriendService;
import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.global.response.PageResponse;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    // FRIEND-02 GET /friends?page=0&size=20 📄
    @GetMapping
    public ApiResponse<PageResponse<FriendResponse>> getFriends(@AuthenticationPrincipal Long userId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(friendService.getFriends(userId, page, size));
    }

    // FRIEND-04 POST /friends/{friendId}/nudge → 204 (같은 친구 하루 1회)
    @PostMapping("/{friendId}/nudge")
    public ResponseEntity<Void> nudge(@AuthenticationPrincipal Long userId, @PathVariable Long friendId) {
        friendService.nudge(userId, friendId);
        return ResponseEntity.noContent().build();
    }

    // FRIEND-01 POST /friends/invites → 201
    @PostMapping("/invites")
    public ResponseEntity<ApiResponse<InviteResponse>> createInvite(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(friendService.createInvite(userId)));
    }

    // FRIEND-01 POST /friends/invites/{inviteCode}/accept
    @PostMapping("/invites/{inviteCode}/accept")
    public ApiResponse<InviteAcceptResponse> acceptInvite(@AuthenticationPrincipal Long userId,
                                                          @PathVariable String inviteCode) {
        return ApiResponse.ok(friendService.acceptInvite(userId, inviteCode));
    }
}
