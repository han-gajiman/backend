package com.hangajiman.wish.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.domain.SpotVisit;
import com.hangajiman.spot.repository.SpotRepository;
import com.hangajiman.spot.repository.SpotVisitRepository;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.user.service.UserService;
import com.hangajiman.wish.domain.YearReview;
import com.hangajiman.wish.dto.WishDtos.RoundSummary;
import com.hangajiman.wish.dto.WishDtos.RoundVisit;
import com.hangajiman.wish.dto.WishDtos.YearReviewResponse;
import com.hangajiman.wish.repository.WishRepository;
import com.hangajiman.wish.repository.YearReviewRepository;

/**
 * TOWER-03 회차 목록과 TOWER-04 1년 회고: 첫 돌탑 시작일부터 365일이 지날 때마다 그 회차의 회고를 쓸 수 있습니다.
 * 돌탑이 12개가 아니어도 되고, 돌탑이 하나도 없는 회차는 쓰지 않습니다. 쓴 뒤에는 수정할 수 없습니다.
 * 알림 target_id = 끝난 회차 번호 → 앱에서 그 회차 모아보기로 이동. 같은 회차는 한 번만 보냅니다.
 * 매시간 확인하고(서버가 꺼져 있던 날도 따라잡음), 시연용 시간 이동 직후에도 실행합니다.
 */
@Service
public class YearReviewService {

    private static final Logger log = LoggerFactory.getLogger(YearReviewService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final WishRepository wishRepository;
    private final YearReviewRepository yearReviewRepository;
    private final SpotVisitRepository spotVisitRepository;
    private final SpotRepository spotRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public YearReviewService(UserRepository userRepository, UserService userService, WishRepository wishRepository,
                             YearReviewRepository yearReviewRepository, SpotVisitRepository spotVisitRepository,
                             SpotRepository spotRepository, NotificationService notificationService, Clock clock) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.wishRepository = wishRepository;
        this.yearReviewRepository = yearReviewRepository;
        this.spotVisitRepository = spotVisitRepository;
        this.spotRepository = spotRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // GET /wishes/rounds: 돌탑이 있는 회차 + 현재 회차, 오래된 순
    @Transactional(readOnly = true)
    public List<RoundSummary> getRounds(Long userId) {
        User user = userService.getUser(userId);
        int currentRound = user.roundOf(LocalDate.now(clock));
        Map<Integer, Long> towerCounts = new TreeMap<>();
        towerCounts.put(currentRound, 0L);
        wishRepository.countTowersByRound(userId)
                .forEach(row -> towerCounts.put(row.getRound(), row.getTowerCount()));
        Map<Integer, String> reviews = yearReviewRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(YearReview::getRound, YearReview::getContent));
        Map<Integer, List<RoundVisit>> visits = visitsByRound(user);

        return towerCounts.entrySet().stream()
                .map(entry -> {
                    int round = entry.getKey();
                    String review = reviews.get(round);
                    return new RoundSummary(round, entry.getValue(), user.reviewDateOf(round),
                            review == null && round < currentRound && entry.getValue() > 0, review,
                            visits.getOrDefault(round, List.of()));
                })
                .toList();
    }

    // 방문 인증을 인증한 날이 속한 회차로 묶습니다. 오래된 순
    private Map<Integer, List<RoundVisit>> visitsByRound(User user) {
        List<SpotVisit> visits = spotVisitRepository.findByUserIdOrderByVerifiedAtAsc(user.getId());
        Map<Long, String> spotNames = spotRepository.findAllById(visits.stream().map(SpotVisit::getSpotId).toList()).stream()
                .collect(Collectors.toMap(Spot::getId, Spot::getName));
        return visits.stream()
                .map(visit -> new RoundVisit(visit.getSpotId(), spotNames.get(visit.getSpotId()),
                        LocalDate.ofInstant(visit.getVerifiedAt(), clock.getZone())))
                .collect(Collectors.groupingBy(visit -> user.roundOf(visit.visitedOn())));
    }

    // POST /wishes/rounds/{round}/review: 끝난 회차에 돌탑이 있을 때 한 번만
    @Transactional
    public YearReviewResponse writeReview(Long userId, int round, String content) {
        int currentRound = userService.getUser(userId).roundOf(LocalDate.now(clock));
        if (round >= currentRound || !hasTower(userId, round)) {
            throw new BusinessException(ErrorCode.YEAR_REVIEW_NOT_AVAILABLE);
        }
        if (yearReviewRepository.existsByUserIdAndRound(userId, round)) {
            throw new BusinessException(ErrorCode.YEAR_REVIEW_ALREADY);
        }
        try {
            YearReview review = yearReviewRepository.saveAndFlush(new YearReview(userId, round, content.trim()));
            return new YearReviewResponse(review.getRound(), review.getContent());
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 쓴 경우 UNIQUE(user_id, round)가 막습니다.
            throw new BusinessException(ErrorCode.YEAR_REVIEW_ALREADY);
        }
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void scheduled() {
        notifyDue();
    }

    // 보낸 알림 수
    @Transactional
    public int notifyDue() {
        LocalDate today = LocalDate.now(clock);
        int sent = 0;
        for (User user : userRepository.findByJourneyStartedOnLessThanEqual(today.minusDays(User.DAYS_PER_ROUND))) {
            if (notifyIfDue(user, today)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("1년 회고 알림: {}명 (기준일 {})", sent, today);
        }
        return sent;
    }

    // 계정별 시연 시간 이동 (DEMO-01): 그 회원만, 그 회원의 날짜 기준
    @Transactional
    public boolean notifyDueFor(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        LocalDate today = LocalDate.now(clock);
        return user != null && user.getJourneyStartedOn() != null
                && !user.getJourneyStartedOn().isAfter(today.minusDays(User.DAYS_PER_ROUND))
                && notifyIfDue(user, today);
    }

    private boolean notifyIfDue(User user, LocalDate today) {
        int finishedRound = (int) (ChronoUnit.DAYS.between(user.getJourneyStartedOn(), today) / User.DAYS_PER_ROUND);
        if (!hasTower(user.getId(), finishedRound)
                || notificationService.hasNotified(user.getId(), NotificationType.YEAR_REVIEW, (long) finishedRound)) {
            return false;
        }
        notificationService.notify(user.getId(), null, NotificationType.YEAR_REVIEW, (long) finishedRound,
                "1년이 지났어요. 그동안 쌓은 돌탑을 돌아봐요");
        return true;
    }

    private boolean hasTower(Long userId, int round) {
        return wishRepository.countTowersByRound(userId).stream()
                .anyMatch(row -> row.getRound() == round && row.getTowerCount() > 0);
    }
}
