package com.hangajiman.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;

// 실제 PostgreSQL(hangajiman_test). 각 테스트는 끝나면 롤백됩니다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFindBySocialAccount() {
        userRepository.saveAndFlush(User.create(SocialProvider.GOOGLE, "google-123", null));

        User found = userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-123").orElseThrow();

        assertThat(found.getId()).isNotNull();
        assertThat(found.isOnboarded()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(userRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "google-123")).isEmpty();
    }

    @Test
    void onboardingAndJourneyStart() {
        User user = User.create(SocialProvider.KAKAO, "kakao-1", null);
        user.completeOnboarding("소원지기", Instant.parse("2026-10-27T00:00:00Z"));
        user.startJourneyIfFirst(LocalDate.of(2026, 10, 27));
        user.startJourneyIfFirst(LocalDate.of(2026, 11, 30)); // 두 번째부터는 무시
        userRepository.saveAndFlush(user);

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.isOnboarded()).isTrue();
        assertThat(found.getNickname()).isEqualTo("소원지기");
        assertThat(found.getJourneyStartedOn()).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    void sameSocialAccountCannotJoinTwice() {
        userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "kakao-dup", null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "kakao-dup", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
