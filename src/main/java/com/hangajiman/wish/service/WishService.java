package com.hangajiman.wish.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.stone.repository.StoneRepository;
import com.hangajiman.stone.repository.StoneRepository.StoneCount;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.service.UserService;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.dto.WishDtos.CreateWishRequest;
import com.hangajiman.wish.dto.WishDtos.CreateWishResponse;
import com.hangajiman.wish.dto.WishDtos.CurrentWishResponse;
import com.hangajiman.wish.dto.WishDtos.ReflectionRequest;
import com.hangajiman.wish.dto.WishDtos.SpotSummary;
import com.hangajiman.wish.dto.WishDtos.WishSummary;
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

    // WISH-04 공개 범위 변경 (본인 돌탑만). 친구 목록·응원·콕 찌르기는 FRIENDS일 때만 됩니다.
    @Transactional
    public void changeVisibility(Long userId, Long wishId, Visibility visibility) {
        findOwnedWish(userId, wishId).changeVisibility(visibility);
    }

    /**
     * TOWER-02 완성 회고 → CLOSED. 30일이 지난 돌탑만 (아니면 403 TOWER_NOT_COMPLETED).
     * 회고 후에는 진행 중 돌탑이 없어져 새 소원을 시작할 수 있습니다.
     */
    @Transactional
    public void reflect(Long userId, Long wishId, ReflectionRequest request) {
        Wish wish = findOwnedWish(userId, wishId);
        if (wish.getStatus() == WishStatus.CLOSED) {
            throw new BusinessException(ErrorCode.TOWER_NOT_COMPLETED, "이미 회고를 남긴 돌탑이에요.");
        }
        if (!wish.isReflectableOn(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.TOWER_NOT_COMPLETED);
        }

        String reflection = request.reflection() == null || request.reflection().isBlank()
                ? null : request.reflection().trim();
        wish.close(request.result(), reflection);
    }

    /**
     * TOWER-03 회차별 돌탑 모아보기. 첫 돌탑 시작일부터 1년 = 1회차, 완성일 기준 배정 (최대 12개).
     * round를 생략하면 현재 회차. 진행 중(ACTIVE) 돌탑은 빼고 완성(COMPLETED)·회고 완료(CLOSED)만 보여줍니다.
     */
    public List<WishSummary> getCollection(Long userId, Integer round) {
        if (round != null && round < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회차는 1부터예요.");
        }
        int targetRound = round != null ? round : userService.getUser(userId).roundOf(LocalDate.now(clock));
        List<Wish> wishes = wishRepository.findByUserIdAndRoundAndStatusInOrderByStartDateAsc(
                userId, targetRound, List.of(WishStatus.COMPLETED, WishStatus.CLOSED));
        if (wishes.isEmpty()) {
            return List.of();
        }

        List<Long> wishIds = wishes.stream().map(Wish::getId).toList();
        Map<Long, Long> stoneCounts = stoneRepository.countByWishIds(wishIds).stream()
                .collect(Collectors.toMap(StoneCount::getWishId, StoneCount::getStoneCount));
        Set<Long> visited = Set.copyOf(wishRepository.findVisitedWishIds(wishIds));
        Map<Long, String> spotNames = spotRepository.findAllById(
                        wishes.stream().map(Wish::getSpotId).distinct().toList()).stream()
                .collect(Collectors.toMap(Spot::getId, Spot::getName));

        return wishes.stream()
                .map(w -> new WishSummary(w.getId(), w.getContent(), w.getStartDate(),
                        stoneCounts.getOrDefault(w.getId(), 0L), w.getSpotId(), spotNames.get(w.getSpotId()),
                        visited.contains(w.getId()), w.getResult()))
                .toList();
    }

    // 없는 돌탑 404 WISH_NOT_FOUND, 남의 돌탑 403 WISH_NOT_OWNER
    private Wish findOwnedWish(Long userId, Long wishId) {
        Wish wish = wishRepository.findById(wishId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WISH_NOT_FOUND));
        if (!wish.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.WISH_NOT_OWNER);
        }
        return wish;
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
                new SpotSummary(spot.getId(), spot.getName(), spot.getCategory(), visited, spot.getBackgroundImageUrl()),
                stoneRepository.countByWishId(wish.getId()),
                stoneRepository.existsByWishIdAndStoneDate(wish.getId(), today),
                wish.dayIndexOn(today),
                wish.daysLeftOn(today)
        );
    }
}
