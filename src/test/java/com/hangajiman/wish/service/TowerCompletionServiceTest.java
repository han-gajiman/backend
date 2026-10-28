package com.hangajiman.wish.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.repository.WishRepository;

// 실제 DB(hangajiman_test). 테스트마다 롤백됩니다.
@SpringBootTest
@Transactional
class TowerCompletionServiceTest {

    @Autowired
    private TowerCompletionService towerCompletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private Wish wishStartedDaysAgo(int daysAgo) {
        User user = userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "k-" + System.nanoTime(), null));
        LocalDate start = LocalDate.now(clock).minusDays(daysAgo);
        return wishRepository.saveAndFlush(Wish.create(user.getId(), 2L, "소원", "실천", Visibility.PRIVATE, start, 1));
    }

    private WishStatus statusOf(Wish wish) {
        return wishRepository.findById(wish.getId()).orElseThrow().getStatus();
    }

    private List<Map<String, Object>> notificationsOf(Wish wish) {
        return jdbcTemplate.queryForList(
                "SELECT user_id, type, target_id, message FROM notifications WHERE target_id = ? AND type = 'TOWER_COMPLETED'",
                wish.getId());
    }

    @Test
    void completesExpiredWishAndNotifiesOwner() {
        Wish expired = wishStartedDaysAgo(30); // 어제가 30일째

        int count = towerCompletionService.completeExpired();

        assertThat(count).isEqualTo(1);
        assertThat(statusOf(expired)).isEqualTo(WishStatus.COMPLETED);
        List<Map<String, Object>> notifications = notificationsOf(expired);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).get("user_id")).isEqualTo(expired.getUserId());
        assertThat(notifications.get(0).get("message")).isEqualTo("30일 돌탑이 완성됐어요");
    }

    @Test
    void day30IsStillActive() {
        Wish lastDay = wishStartedDaysAgo(29); // 오늘이 30일째

        towerCompletionService.completeExpired();

        assertThat(statusOf(lastDay)).isEqualTo(WishStatus.ACTIVE);
        assertThat(notificationsOf(lastDay)).isEmpty();
    }

    @Test
    void closedWishIsUntouched() {
        Wish closed = wishStartedDaysAgo(60);
        jdbcTemplate.update("UPDATE wishes SET status = 'CLOSED', result = 'ACHIEVED' WHERE id = ?", closed.getId());

        towerCompletionService.completeExpired();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM wishes WHERE id = ?", String.class, closed.getId()))
                .isEqualTo("CLOSED");
        assertThat(notificationsOf(closed)).isEmpty();
    }

    @Test
    void runningTwiceDoesNotDuplicateNotifications() {
        Wish expired = wishStartedDaysAgo(40);

        towerCompletionService.completeExpired();
        int second = towerCompletionService.completeExpired();

        assertThat(second).isZero();
        assertThat(notificationsOf(expired)).hasSize(1);
    }
}
