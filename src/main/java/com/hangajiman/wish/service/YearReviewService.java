package com.hangajiman.wish.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

/**
 * TOWER-04 1년 회고 알림: 첫 돌탑 시작일부터 365일이 지날 때마다 "그 회차 돌탑을 돌아봐요" 알림.
 * target_id = 끝난 회차 번호 → 앱에서 그 회차 모아보기로 이동. 같은 회차는 한 번만 보냅니다.
 * 매시간 확인하고(서버가 꺼져 있던 날도 따라잡음), 시연용 시간 이동 직후에도 실행합니다.
 */
@Service
public class YearReviewService {

    private static final Logger log = LoggerFactory.getLogger(YearReviewService.class);

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public YearReviewService(UserRepository userRepository, NotificationService notificationService, Clock clock) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
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
            long finishedRound = ChronoUnit.DAYS.between(user.getJourneyStartedOn(), today) / User.DAYS_PER_ROUND;
            if (notificationService.hasNotified(user.getId(), NotificationType.YEAR_REVIEW, finishedRound)) {
                continue;
            }
            notificationService.notify(user.getId(), null, NotificationType.YEAR_REVIEW, finishedRound,
                    finishedRound + "회차 1년이 지났어요. 그동안 쌓은 돌탑을 돌아봐요");
            sent++;
        }
        if (sent > 0) {
            log.info("1년 회고 알림: {}명 (기준일 {})", sent, today);
        }
        return sent;
    }
}
