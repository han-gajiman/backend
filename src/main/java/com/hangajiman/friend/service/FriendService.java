package com.hangajiman.friend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.domain.FriendInvite;
import com.hangajiman.friend.domain.Friendship;
import com.hangajiman.friend.dto.InviteAcceptResponse;
import com.hangajiman.friend.dto.InviteResponse;
import com.hangajiman.friend.repository.FriendInviteRepository;
import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class FriendService {

    // 초대 링크 유효 기간 (ERD friend_invites.expires_at: 발급 +7일)
    static final Duration INVITE_TTL = Duration.ofDays(7);

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FriendInviteRepository inviteRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final String inviteBaseUrl;

    public FriendService(FriendInviteRepository inviteRepository,
                         FriendshipRepository friendshipRepository,
                         UserRepository userRepository,
                         Clock clock,
                         @Value("${friend.invite-base-url}") String inviteBaseUrl) {
        this.inviteRepository = inviteRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.inviteBaseUrl = inviteBaseUrl.replaceAll("/+$", "");
    }

    // FRIEND-01 초대 링크 생성
    @Transactional
    public InviteResponse createInvite(Long userId) {
        Instant expiresAt = clock.instant().plus(INVITE_TTL);
        FriendInvite invite = inviteRepository.save(new FriendInvite(userId, newInviteCode(), expiresAt));
        return new InviteResponse(
                invite.getInviteCode(),
                inviteBaseUrl + "/invite/" + invite.getInviteCode(),
                OffsetDateTime.ofInstant(expiresAt, ClockConfig.KST));
    }

    // FRIEND-01 초대 수락 → 양방향 친구 관계 저장
    @Transactional
    public InviteAcceptResponse acceptInvite(Long userId, String inviteCode) {
        FriendInvite invite = inviteRepository.findByInviteCode(inviteCode)
                .filter(found -> !found.isExpired(clock.instant()))
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_INVITE_NOT_FOUND));

        Long inviterId = invite.getInviterId();
        if (inviterId.equals(userId)) {
            throw new BusinessException(ErrorCode.FRIEND_SELF_INVITE);
        }
        if (friendshipRepository.existsByUserIdAndFriendId(userId, inviterId)) {
            throw new BusinessException(ErrorCode.FRIEND_ALREADY);
        }
        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_INVITE_NOT_FOUND));

        try {
            friendshipRepository.save(new Friendship(userId, inviterId));
            friendshipRepository.saveAndFlush(new Friendship(inviterId, userId));
        } catch (DataIntegrityViolationException e) {
            // 같은 초대를 동시에 두 번 수락한 경우 UNIQUE(user_id, friend_id)에 걸립니다.
            throw new BusinessException(ErrorCode.FRIEND_ALREADY);
        }
        return new InviteAcceptResponse(inviterId, inviter.getNickname());
    }

    private String newInviteCode() {
        for (int attempt = 0; attempt < CODE_MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!inviteRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성 실패");
    }

    private static String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return code.toString();
    }
}
