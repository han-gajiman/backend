package com.hangajiman.wish.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.stone.repository.StoneRepository;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.service.UserService;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.dto.WishDtos.CreateWishRequest;
import com.hangajiman.wish.dto.WishDtos.CreateWishResponse;
import com.hangajiman.wish.dto.WishDtos.CurrentWishResponse;
import com.hangajiman.wish.dto.WishDtos.SpotSummary;
import com.hangajiman.wish.repository.WishRepository;

@Service
@Transactional(readOnly = true)
public class WishService {

    private final WishRepository wishRepository;
    private final StoneRepository stoneRepository;
    private final SpotRepository spotRepository;
    private final UserService userService;
    private final Clock clock;

    public WishService(WishRepository wishRepository, StoneRepository stoneRepository, SpotRepository spotRepository,
                       UserService userService, Clock clock) {
        this.wishRepository = wishRepository;
        this.stoneRepository = stoneRepository;
        this.spotRepository = spotRepository;
        this.userService = userService;
        this.clock = clock;
    }

    /**
     * WISH-03 소원 등록 (돌탑 시작). 등록 후 수정·취소 API는 없습니다.
     * 진행 중 돌탑이 있으면 409. 첫 돌탑이면 회차 기준일(journey_started_on)을 기록합니다.
     */
    @Transactional
    public CreateWishResponse create(Long userId, CreateWishRequest request) {
        if (!spotRepository.existsById(request.spotId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "없는 명소예요.");
        }
        if (wishRepository.existsByUserIdAndStatusIn(userId, WishRepository.OPEN_STATUSES)) {
            throw new BusinessException(ErrorCode.WISH_ALREADY_ACTIVE);
        }

        User user = userService.getUser(userId);
        LocalDate today = LocalDate.now(clock);
        user.startJourneyIfFirst(today);

        Visibility visibility = request.visibility() == null ? Visibility.PRIVATE : request.visibility();
        Wish wish = Wish.create(userId, request.spotId(), request.content().trim(), request.actionGoal().trim(),
                visibility, today, user.roundOf(Wish.endDateOf(today)));
        try {
            wishRepository.saveAndFlush(wish);
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 등록한 경우 DB 부분 유니크 인덱스(uq_wishes_one_open)가 막습니다.
            throw new BusinessException(ErrorCode.WISH_ALREADY_ACTIVE);
        }
        return new CreateWishResponse(wish.getId(), wish.getStartDate(), wish.getEndDate());
    }

    // GET /wishes/current. 진행 중(ACTIVE/COMPLETED) 돌탑이 없으면 null
    public CurrentWishResponse getCurrent(Long userId) {
        return wishRepository.findFirstByUserIdAndStatusIn(userId, WishRepository.OPEN_STATUSES)
                .map(this::toCurrentResponse)
                .orElse(null);
    }

    private CurrentWishResponse toCurrentResponse(Wish wish) {
        LocalDate today = LocalDate.now(clock);
        Spot spot = spotRepository.findById(wish.getSpotId()).orElseThrow();
        boolean visited = wishRepository.existsSpotVisit(wish.getId());

        return new CurrentWishResponse(
                wish.getId(),
                wish.getContent(),
                wish.getActionGoal(),
                wish.getStatus(),
                wish.getVisibility(),
                new SpotSummary(spot.getId(), spot.getName(), spot.getCategory(), visited,
                        visited ? spot.getBackgroundImageUrl() : null),
                stoneRepository.countByWishId(wish.getId()),
                stoneRepository.existsByWishIdAndStoneDate(wish.getId(), today),
                wish.dayIndexOn(today),
                wish.daysLeftOn(today)
        );
    }
}
