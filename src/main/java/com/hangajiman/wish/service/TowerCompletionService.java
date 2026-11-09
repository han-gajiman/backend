package com.hangajiman.wish.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.repository.WishRepository;

/**
 * TOWER-01 30일 완성 처리: 종료일이 지난 ACTIVE 돌탑을 COMPLETED(회고 대기)로 바꾸고 주인에게 완성 알림(+푸시).
 * 매시간 실행해서 서버가 자정에 꺼져 있었어도 다음 정각에 처리됩니다. 시연용 시간 이동 직후에도 바로 실행합니다.
 */
@Service
public class TowerCompletionService {

    static final String COMPLETED_MESSAGE = "30일 돌탑이 완성됐어요";

    private static final Logger log = LoggerFactory.getLogger(TowerCompletionService.class);

    private final WishRepository wishRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public TowerCompletionService(WishRepository wishRepository, NotificationService notificationService, Clock clock) {
        this.wishRepository = wishRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void scheduled() {
        completeExpired();
    }

    // 계정별 시연 시간 이동 (DEMO-01): 그 회원의 돌탑만, 그 회원의 날짜(요청에 묶인 시계) 기준으로 완성 처리
    @Transactional
    public int completeExpiredFor(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<Wish> expired = wishRepository.findByUserIdAndStatusAndEndDateBefore(userId, WishStatus.ACTIVE, today);
        for (Wish wish : expired) {
            wish.complete();
            notificationService.notify(wish.getUserId(), null, NotificationType.TOWER_COMPLETED, wish.getId(),
                    COMPLETED_MESSAGE);
        }
        return expired.size();
    }

    // 완성 처리한 돌탑 수
    @Transactional
    public int completeExpired() {
        LocalDate today = LocalDate.now(clock);
        List<Wish> expired = wishRepository.findByStatusAndEndDateBefore(WishStatus.ACTIVE, today);
        for (Wish wish : expired) {
            notificationService.notify(wish.getUserId(), null, NotificationType.TOWER_COMPLETED, wish.getId(),
                    COMPLETED_MESSAGE);
        }
        int completed = wishRepository.completeExpired(today);
        if (completed > 0) {
            log.info("30일 돌탑 완성 처리: {}개 (기준일 {})", completed, today);
        }
        return completed;
    }
}
