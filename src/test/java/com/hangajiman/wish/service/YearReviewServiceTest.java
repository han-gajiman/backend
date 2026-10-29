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

// TOWER-04 1년 회고 알림. 실제 DB(hangajiman_test), 테스트마다 롤백
@SpringBootTest
@Transactional
class YearReviewServiceTest {

    @Autowired
    private YearReviewService yearReviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private User userStartedDaysAgo(int daysAgo) {
        User user = User.create(SocialProvider.KAKAO, "k-" + System.nanoTime(), null);
        user.startJourneyIfFirst(LocalDate.now(clock).minusDays(daysAgo));
        return userRepository.saveAndFlush(user);
    }

    private List<Map<String, Object>> reviewsOf(User user) {
        return jdbcTemplate.queryForList(
                "SELECT target_id, message FROM notifications WHERE user_id = ? AND type = 'YEAR_REVIEW'", user.getId());
    }

    @Test
    void notifiesAfter365Days() {
        User user = userStartedDaysAgo(365);

        yearReviewService.notifyDue();

        List<Map<String, Object>> reviews = reviewsOf(user);
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).get("target_id")).isEqualTo(1L);
        assertThat(reviews.get(0).get("message")).isEqualTo("1회차 1년이 지났어요. 그동안 쌓은 돌탑을 돌아봐요");
    }

    @Test
    void notBefore365Days() {
        User user = userStartedDaysAgo(364);

        yearReviewService.notifyDue();

        assertThat(reviewsOf(user)).isEmpty();
    }

    @Test
    void secondYearSendsRound2() {
        User user = userStartedDaysAgo(730);

        yearReviewService.notifyDue();

        assertThat(reviewsOf(user)).extracting(r -> r.get("target_id")).containsExactly(2L);
    }

    @Test
    void sendsOncePerRound() {
        User user = userStartedDaysAgo(400);

        yearReviewService.notifyDue();
        yearReviewService.notifyDue();

        assertThat(reviewsOf(user)).hasSize(1);
    }

    @Test
    void usersWithoutJourneyAreSkipped() {
        User user = userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "k-" + System.nanoTime(), null));

        yearReviewService.notifyDue();

        assertThat(reviewsOf(user)).isEmpty();
    }
}
