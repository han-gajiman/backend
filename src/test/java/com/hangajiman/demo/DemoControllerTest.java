package com.hangajiman.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

// local 프로필에서만 시간 이동 API가 등록됩니다. 시계는 테스트마다 원래대로 돌립니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private DemoClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private User onboardedUser() {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        return userRepository.saveAndFlush(user);
    }

    @Test
    void travel30DaysCompletesTowerAndTokenStillWorks() throws Exception {
        User user = onboardedUser();
        LocalDate today = LocalDate.now(clock);
        wishRepository.saveAndFlush(Wish.create(user.getId(), 2L, "소원", "실천", Visibility.PRIVATE, today, 1));
        String bearer = "Bearer " + jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(post("/api/v1/demo/time-travel").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\": 30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverNow", Matchers.startsWith(today.plusDays(30).toString())));

        // 같은 토큰으로 계속 호출 가능 (토큰 만료는 실제 시간 기준)
        mockMvc.perform(get("/api/v1/wishes/current").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.dayIndex").value(30))
                .andExpect(jsonPath("$.data.daysLeft").value(0));
    }

    @Test
    void travelOneDayKeepsTowerActive() throws Exception {
        User user = onboardedUser();
        LocalDate today = LocalDate.now(clock);
        wishRepository.saveAndFlush(Wish.create(user.getId(), 2L, "소원", "실천", Visibility.PRIVATE, today, 1));
        String bearer = "Bearer " + jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(post("/api/v1/demo/time-travel").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\": 1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/wishes/current").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.dayIndex").value(2))
                .andExpect(jsonPath("$.data.stonedToday").value(false));
    }

    @Test
    void invalidDaysAreRejected() throws Exception {
        String bearer = "Bearer " + jwtProvider.createAccessToken(onboardedUser().getId());

        for (String days : new String[]{"0", "-1", "366"}) {
            mockMvc.perform(post("/api/v1/demo/time-travel").header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"days\": " + days + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }
}
