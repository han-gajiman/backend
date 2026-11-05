package com.hangajiman.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

// 실제 DB(hangajiman_test). 테스트마다 롤백됩니다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String ONBOARDING_BODY = """
            {"nickname": "소원지기",
             "agreements": {"terms": true, "privacy": true},
             "agreementVersion": "1.0"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private User newUser() {
        return userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null));
    }

    private User onboardedUser() {
        User user = newUser();
        user.completeOnboarding("소원지기", clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void onboardingThenMe() throws Exception {
        User user = newUser();

        mockMvc.perform(as(user, post("/api/v1/users/me/onboarding")).content(ONBOARDING_BODY))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(user, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("소원지기"))
                .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.agreements").doesNotExist())
                .andExpect(jsonPath("$.data.hasActiveWish").value(false))
                .andExpect(jsonPath("$.data.journey").doesNotExist());
    }

    @Test
    void onboardingWithoutRequiredAgreementFails() throws Exception {
        User user = newUser();
        String body = ONBOARDING_BODY.replace("\"privacy\": true", "\"privacy\": false");

        mockMvc.perform(as(user, post("/api/v1/users/me/onboarding")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUIRED_AGREEMENT_MISSING"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().isOnboarded()).isFalse();
    }

    @Test
    void onboardingWithTooLongNicknameFails() throws Exception {
        User user = newUser();
        String body = ONBOARDING_BODY.replace("소원지기", "가".repeat(31));

        mockMvc.perform(as(user, post("/api/v1/users/me/onboarding")).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void otherApisAreBlockedBeforeOnboarding() throws Exception {
        User user = newUser();

        mockMvc.perform(as(user, post("/api/v1/spots/1/visits")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));

        // 내 정보는 온보딩 전에도 조회 가능
        mockMvc.perform(as(user, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").isEmpty());
    }

    @Test
    void registerPushToken() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, put("/api/v1/users/me/push-token"))
                        .content("{\"pushToken\": \"ExponentPushToken[abc]\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPushToken())
                .isEqualTo("ExponentPushToken[abc]");
    }

    @Test
    void activeWish() throws Exception {
        User user = onboardedUser();
        LocalDate today = LocalDate.now(clock);
        jdbcTemplate.update("""
                INSERT INTO wishes (user_id, spot_id, content, action_goal, start_date, end_date, round)
                VALUES (?, 1, '소원', '실천', ?, ?, 1)
                """, user.getId(), today, today.plusDays(29));

        mockMvc.perform(as(user, get("/api/v1/users/me")))
                .andExpect(jsonPath("$.data.hasActiveWish").value(true));
    }

    @Test
    void tokenOfDeletedUserIsRejected() throws Exception {
        String token = jwtProvider.createAccessToken(Long.MAX_VALUE);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }
}
