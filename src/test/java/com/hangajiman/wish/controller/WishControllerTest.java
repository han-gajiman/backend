package com.hangajiman.wish.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.stone.domain.Stone;
import com.hangajiman.stone.repository.StoneRepository;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

// 실제 DB(hangajiman_test). 테스트마다 롤백됩니다. 명소 id 2 = 문경새재 과거길(시드)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishControllerTest {

    private static final String CREATE_BODY = """
            {"content": "백엔드 개발자로 취업하기", "actionGoal": "매일 영어 단어 20개 외우기", "spotId": 2}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private StoneRepository stoneRepository;

    @Autowired
    private Clock clock;

    private User onboardedUser() {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    @Test
    void createThenCurrent() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.wishId").isNumber())
                .andExpect(jsonPath("$.data.startDate").value(today().toString()))
                .andExpect(jsonPath("$.data.endDate").value(today().plusDays(29).toString()));

        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("백엔드 개발자로 취업하기"))
                .andExpect(jsonPath("$.data.actionGoal").value("매일 영어 단어 20개 외우기"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.spot.name").value("문경새재 과거길"))
                .andExpect(jsonPath("$.data.spot.category").value("PASS"))
                .andExpect(jsonPath("$.data.spot.visited").value(false))
                .andExpect(jsonPath("$.data.spot.backgroundImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.stoneCount").value(0))
                .andExpect(jsonPath("$.data.stonedToday").value(false))
                .andExpect(jsonPath("$.data.dayIndex").value(1))
                .andExpect(jsonPath("$.data.daysLeft").value(29));
    }

    @Test
    void firstWishStartsJourney() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY)).andExpect(status().isCreated());

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getJourneyStartedOn()).isEqualTo(today());
        assertThat(wishRepository.findFirstByUserIdAndStatusIn(user.getId(), WishRepository.OPEN_STATUSES)
                .orElseThrow().getRound()).isEqualTo(1);
    }

    @Test
    void roundIsBasedOnEndDate() throws Exception {
        User user = onboardedUser();
        user.startJourneyIfFirst(today().minusDays(350)); // 끝나는 날(+29)은 379일째 → 2회차
        userRepository.saveAndFlush(user);

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY)).andExpect(status().isCreated());

        assertThat(wishRepository.findFirstByUserIdAndStatusIn(user.getId(), WishRepository.OPEN_STATUSES)
                .orElseThrow().getRound()).isEqualTo(2);
    }

    @Test
    void visibilityCanBeChosen() throws Exception {
        User user = onboardedUser();
        String body = CREATE_BODY.replace("\"spotId\": 2", "\"spotId\": 2, \"visibility\": \"FRIENDS\"");

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(body)).andExpect(status().isCreated());
        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.visibility").value("FRIENDS"));
    }

    @Test
    void secondWishWhileActiveIsRejected() throws Exception {
        User user = onboardedUser();
        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY)).andExpect(status().isCreated());

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WISH_ALREADY_ACTIVE"));
    }

    @Test
    void unknownSpotIsRejected() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY.replace("\"spotId\": 2", "\"spotId\": 999")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("없는 명소예요."));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/wishes"))
                        .content(CREATE_BODY.replace("백엔드 개발자로 취업하기", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(as(user, post("/api/v1/wishes"))
                        .content(CREATE_BODY.replace("매일 영어 단어 20개 외우기", "가".repeat(101))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post("/api/v1/wishes"))
                        .content(CREATE_BODY.replace("\"spotId\": 2", "\"spotId\": 2, \"visibility\": \"PUBLIC\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noCurrentWishReturnsNull() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void dayIndexAndStonesUseServerDate() throws Exception {
        User user = onboardedUser();
        LocalDate start = today().minusDays(12); // 13일째
        Wish wish = wishRepository.saveAndFlush(Wish.create(user.getId(), 2L, "소원", "실천", Visibility.PRIVATE, start, 1));
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), start, null));
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), today().minusDays(1), "단어 20개"));

        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.dayIndex").value(13))
                .andExpect(jsonPath("$.data.daysLeft").value(17))
                .andExpect(jsonPath("$.data.stoneCount").value(2))
                .andExpect(jsonPath("$.data.stonedToday").value(false));

        stoneRepository.saveAndFlush(Stone.of(wish.getId(), today(), null));
        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.stoneCount").value(3))
                .andExpect(jsonPath("$.data.stonedToday").value(true));
    }

    @Test
    void otherUsersWishIsNotShown() throws Exception {
        User owner = onboardedUser();
        User other = onboardedUser();
        mockMvc.perform(as(owner, post("/api/v1/wishes")).content(CREATE_BODY)).andExpect(status().isCreated());

        mockMvc.perform(as(other, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void timeTravelDoesNotExistOutsideLocalOrDemoProfile() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/demo/time-travel")).content("{\"days\": 30}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(as(user, get("/api/v1/wishes/current"))).andExpect(status().isOk());
    }

    @Test
    void requiresOnboarding() throws Exception {
        User user = userRepository.saveAndFlush(User.create(SocialProvider.KAKAO, "not-onboarded", null));

        mockMvc.perform(as(user, post("/api/v1/wishes")).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));
    }
}
