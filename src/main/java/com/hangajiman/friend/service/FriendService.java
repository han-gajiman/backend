package com.hangajiman.friend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.domain.FriendInvite;
import com.hangajiman.friend.domain.Friendship;
import com.hangajiman.friend.dto.FriendResponse;
import com.hangajiman.friend.dto.InviteAcceptResponse;
import com.hangajiman.friend.dto.InviteResponse;
import com.hangajiman.friend.repository.FriendInviteRepository;
import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

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
    private final NotificationService notificationService;
    private final Clock clock;
    private final String inviteBaseUrl;

    public FriendService(FriendInviteRepository inviteRepository,
                         FriendshipRepository friendshipRepository,
                         UserRepository userRepository,
                         NotificationService notificationService,
                         Clock clock,
                         @Value("${friend.invite-base-url}") String inviteBaseUrl) {
        this.inviteRepository = inviteRepository;
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
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

    /**
     * FRIEND-02 친구 목록 + 돌탑 요약 (최근에 친구가 된 순).
     * 돌탑 요약은 친구의 진행 중(ACTIVE) 돌탑이 친구 공개(FRIENDS)일 때만 보여줍니다.
     */
    public PageResponse<FriendResponse> getFriends(Long userId, int page, int size) {
        Slice<Friendship> friendships = friendshipRepository.findByUserId(userId,
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<Long> friendIds = friendships.getContent().stream().map(Friendship::getFriendId).toList();
        if (friendIds.isEmpty()) {
            return new PageResponse<>(List.of(), friendships.getNumber(), friendships.getSize(), friendships.hasNext());
        }

        Map<Long, User> users = userRepository.findAllById(friendIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Wish> visibleWishes = friendshipRepository.findWishesOf(friendIds, WishStatus.ACTIVE).stream()
                .filter(wish -> wish.getVisibility() == Visibility.FRIENDS)
                .collect(Collectors.toMap(Wish::getUserId, Function.identity()));

        List<Long> wishIds = visibleWishes.values().stream().map(Wish::getId).toList();
        Map<Long, Long> stoneCounts = wishIds.isEmpty() ? Map.of()
                : friendshipRepository.countStones(wishIds).stream()
                        .collect(Collectors.toMap(FriendshipRepository.WishStoneCount::getWishId,
                                FriendshipRepository.WishStoneCount::getStoneCount));
        Set<Long> stonedToday = wishIds.isEmpty() ? Set.of()
                : Set.copyOf(friendshipRepository.findWishIdsStonedOn(wishIds, LocalDate.now(clock)));

        List<FriendResponse> content = friendIds.stream()
                .filter(users::containsKey)
                .map(friendId -> {
                    Wish wish = visibleWishes.get(friendId);
                    FriendResponse.WishSummary summary = wish == null ? null : new FriendResponse.WishSummary(
                            wish.getId(),
                            wish.getContent(),
                            stoneCounts.getOrDefault(wish.getId(), 0L),
                            stonedToday.contains(wish.getId()));
                    return new FriendResponse(friendId, users.get(friendId).getNickname(), summary);
                })
                .toList();
        return new PageResponse<>(content, friendships.getNumber(), friendships.getSize(), friendships.hasNext());
    }

    /**
     * FRIEND-04 콕 찌르기: 오늘(KST) 아직 돌을 안 쌓은 친구에게 알림. 같은 친구에게는 하루 1회.
     * 친구 아님(본인 포함) → 404 FRIEND_NOT_FOUND
     * 친구 공개 진행 중 돌탑이 없거나 오늘 이미 쌓음 → 400 NUDGE_NOT_ALLOWED
     * 오늘 이미 찌름 → 429 NUDGE_LIMIT
     * 받는 사람에게 NUDGE 알림 (알림함 저장 + Expo 푸시, NOTI-01).
     */
    @Transactional
    public void nudge(Long userId, Long friendId) {
        if (!friendshipRepository.existsByUserIdAndFriendId(userId, friendId)) {
            throw new BusinessException(ErrorCode.FRIEND_NOT_FOUND);
        }

        LocalDate today = LocalDate.now(clock);
        Wish wish = friendshipRepository.findWishesOf(List.of(friendId), WishStatus.ACTIVE).stream()
                .filter(found -> found.getVisibility() == Visibility.FRIENDS)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NUDGE_NOT_ALLOWED));
        if (!friendshipRepository.findWishIdsStonedOn(List.of(wish.getId()), today).isEmpty()) {
            throw new BusinessException(ErrorCode.NUDGE_NOT_ALLOWED);
        }

        Instant startOfToday = today.atStartOfDay(ClockConfig.KST).toInstant();
        if (notificationService.hasSentSince(NotificationType.NUDGE, userId, friendId, startOfToday)) {
            throw new BusinessException(ErrorCode.NUDGE_LIMIT);
        }

        User me = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
        notificationService.notify(friendId, userId, NotificationType.NUDGE, wish.getId(),
                me.getNickname() + "님이 콕 찔렀어요! 오늘의 돌을 쌓아 보세요.");
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
