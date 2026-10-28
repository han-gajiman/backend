package com.hangajiman.friend.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.domain.Cheer;
import com.hangajiman.friend.dto.CheerCreateResponse;
import com.hangajiman.friend.dto.CheerResponse;
import com.hangajiman.friend.repository.CheerRepository;
import com.hangajiman.friend.repository.FriendNotificationRepository;
import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.repository.WishRepository;

/**
 * FRIEND-03 응원. 익명 소원지기(FRIEND-05)는 보류라 지금은 친구만 응원할 수 있습니다.
 * 푸시는 알림함(notifications)에 CHEER 행을 저장하는 것까지. 실제 Expo 발송은 NOTI-01에서 붙입니다.
 */
@Service
@Transactional(readOnly = true)
public class CheerService {

    private final CheerRepository cheerRepository;
    private final FriendshipRepository friendshipRepository;
    private final WishRepository wishRepository;
    private final UserRepository userRepository;
    private final FriendNotificationRepository notificationRepository;

    public CheerService(CheerRepository cheerRepository,
                        FriendshipRepository friendshipRepository,
                        WishRepository wishRepository,
                        UserRepository userRepository,
                        FriendNotificationRepository notificationRepository) {
        this.cheerRepository = cheerRepository;
        this.friendshipRepository = friendshipRepository;
        this.wishRepository = wishRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 응원 작성: 친구의 진행 중·친구 공개 돌탑에만. 본인 돌탑, 친구 아님, 비공개 → 403 CHEER_NOT_ALLOWED
     */
    @Transactional
    public CheerCreateResponse writeCheer(Long writerId, Long wishId, String rawMessage) {
        String message = rawMessage.trim();
        if (message.codePointCount(0, message.length()) > Cheer.MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCode.CHEER_TOO_LONG);
        }

        Wish wish = findWish(wishId);
        if (wish.isOwnedBy(writerId) || !canFriendSee(writerId, wish)) {
            throw new BusinessException(ErrorCode.CHEER_NOT_ALLOWED);
        }
        if (wish.getStatus() != WishStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WISH_NOT_ACTIVE);
        }

        Cheer cheer = cheerRepository.save(Cheer.byFriend(wishId, writerId, message));
        User writer = userRepository.findById(writerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
        notificationRepository.saveCheer(wish.getUserId(), writerId, cheer.getId(),
                writer.getNickname() + "님이 응원을 보냈어요.");
        return new CheerCreateResponse(cheer.getId());
    }

    /**
     * 응원 목록 (최신순): 돌탑 주인, 또는 친구 공개 돌탑의 친구만. 그 외 403 CHEER_NOT_ALLOWED
     */
    public PageResponse<CheerResponse> getCheers(Long userId, Long wishId, int page, int size) {
        Wish wish = findWish(wishId);
        if (!wish.isOwnedBy(userId) && !canFriendSee(userId, wish)) {
            throw new BusinessException(ErrorCode.CHEER_NOT_ALLOWED);
        }

        Slice<Cheer> cheers = cheerRepository.findByWishId(wishId,
                PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<Long> writerIds = cheers.getContent().stream().map(Cheer::getWriterId).distinct().toList();
        Map<Long, User> writers = writerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(writerIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<CheerResponse> content = cheers.getContent().stream()
                .map(cheer -> new CheerResponse(
                        cheer.getId(),
                        cheer.isAnonymous() || !writers.containsKey(cheer.getWriterId())
                                ? null : writers.get(cheer.getWriterId()).getNickname(),
                        cheer.isAnonymous(),
                        cheer.getMessage(),
                        OffsetDateTime.ofInstant(cheer.getCreatedAt(), ClockConfig.KST)))
                .toList();
        return new PageResponse<>(content, cheers.getNumber(), cheers.getSize(), cheers.hasNext());
    }

    private Wish findWish(Long wishId) {
        return wishRepository.findById(wishId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));
    }

    // 돌탑 주인의 친구이고, 돌탑이 친구 공개인지
    private boolean canFriendSee(Long userId, Wish wish) {
        return wish.getVisibility() == Visibility.FRIENDS && friendshipRepository.existsByUserIdAndFriendId(userId, wish.getUserId());
    }
}
