package com.hangajiman.stone.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.friend.repository.FriendshipRepository;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.stone.domain.Stone;
import com.hangajiman.stone.dto.StoneDtos.CreateStoneResponse;
import com.hangajiman.stone.dto.StoneDtos.StoneItem;
import com.hangajiman.stone.repository.StoneRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

@Service
@Transactional(readOnly = true)
public class StoneService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("stoneDate"));

    private final StoneRepository stoneRepository;
    private final WishRepository wishRepository;
    private final FriendshipRepository friendshipRepository;
    private final Clock clock;

    public StoneService(StoneRepository stoneRepository, WishRepository wishRepository,
                        FriendshipRepository friendshipRepository, Clock clock) {
        this.stoneRepository = stoneRepository;
        this.wishRepository = wishRepository;
        this.friendshipRepository = friendshipRepository;
        this.clock = clock;
    }

    /**
     * STONE-01 오늘의 돌. 하루(KST) 1개만 — 서비스에서 확인하고 DB UNIQUE(wish_id, stone_date)로도 막습니다.
     */
    @Transactional
    public CreateStoneResponse stack(Long userId, Long wishId, String content) {
        Wish wish = findWish(wishId);
        if (!wish.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.WISH_NOT_OWNER);
        }
        LocalDate today = LocalDate.now(clock);
        if (!wish.isStackableOn(today)) {
            throw new BusinessException(ErrorCode.WISH_NOT_ACTIVE);
        }
        if (stoneRepository.existsByWishIdAndStoneDate(wishId, today)) {
            throw new BusinessException(ErrorCode.STONE_ALREADY_TODAY);
        }

        Stone stone = Stone.of(wishId, today, content == null ? null : content.trim());
        try {
            stoneRepository.saveAndFlush(stone);
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 두 번 누른 경우
            throw new BusinessException(ErrorCode.STONE_ALREADY_TODAY);
        }
        return new CreateStoneResponse(stone.getId(), stone.getStoneDate(), stone.getType(),
                stoneRepository.countByWishId(wishId));
    }

    // 돌 목록 (최신순). 본인 또는 친구 공개 돌탑을 보는 친구만
    public PageResponse<StoneItem> getStones(Long userId, Long wishId, int page, int size) {
        Wish wish = findWish(wishId);
        if (!canView(userId, wish)) {
            throw new BusinessException(ErrorCode.WISH_NOT_OWNER);
        }
        return PageResponse.of(
                stoneRepository.findByWishId(wishId, PageResponse.pageable(page, size, LATEST_FIRST)),
                StoneItem::from);
    }

    private boolean canView(Long userId, Wish wish) {
        return wish.isOwnedBy(userId)
                || (wish.getVisibility() == Visibility.FRIENDS && friendshipRepository.existsByUserIdAndFriendId(userId, wish.getUserId()));
    }

    // 없는 돌탑 404 WISH_NOT_FOUND, 남의 돌탑 403 WISH_NOT_OWNER (응원 API와 같은 기준)
    private Wish findWish(Long wishId) {
        return wishRepository.findById(wishId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));
    }
}
