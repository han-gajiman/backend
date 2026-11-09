package com.hangajiman.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.repository.WishRepository;

// DEMO-01 실서버 방식: 허용된 시연 계정만 자기 날짜를 이동하고, 다른 회원과 스케줄러는 실제 날짜 그대로
@SpringBootTest(properties = "demo.time-travel.user-emails=stage@hangajiman.app")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class PerAccountTimeTravelTest {

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

    private User stage;

    @AfterEach
    void resetClock() {
        if (stage != null) {
            clock.resetUser(stage.getId());
        }
        clock.reset();
    }

    private User user(String email, String nickname) {
        User user = User.createLocal(email, "hash", nickname);
        user.completeOnboarding(nickname, clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private Wish activeWish(User owner) {
        return wishRepository.saveAndFlush(Wish.create(owner.getId(), 1L, "소원", "매일 하기", Visibility.FRIENDS,
                LocalDate.now(clock), 1));
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String today(User user) throws Exception {
        String body = mockMvc.perform(as(user, get("/api/v1/plans/calendar"))).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.today");
    }

    @Test
    void onlyStageAccountMovesItsOwnDate() throws Exception {
        stage = user("stage@hangajiman.app", "시연");
        User other = user("other@hangajiman.app", "다른회원");
        Wish stageWish = activeWish(stage);
        Wish otherWish = activeWish(other);
        String realToday = LocalDate.now(clock).toString();

        mockMvc.perform(as(stage, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.canTimeTravel").value(true));
        mockMvc.perform(as(other, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.canTimeTravel").value(false));

        // 다른 회원은 시간 이동 불가
        mockMvc.perform(as(other, post("/api/v1/demo/time-travel")).content("{\"days\": 30}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DEMO_NOT_ALLOWED"));

        mockMvc.perform(as(stage, post("/api/v1/demo/time-travel")).content("{\"days\": 30}"))
                .andExpect(status().isOk());

        // 시연 계정만 30일 뒤, 그 계정의 돌탑만 완성 처리
        assertThat(today(stage)).isEqualTo(LocalDate.parse(realToday).plusDays(30).toString());
        assertThat(today(other)).isEqualTo(realToday);
        assertThat(wishRepository.findById(stageWish.getId()).orElseThrow().getStatus()).isEqualTo(WishStatus.COMPLETED);
        assertThat(wishRepository.findById(otherWish.getId()).orElseThrow().getStatus()).isEqualTo(WishStatus.ACTIVE);
        // 요청 밖(스케줄러 등)은 실제 날짜
        assertThat(LocalDate.now(clock).toString()).isEqualTo(realToday);

        mockMvc.perform(as(stage, post("/api/v1/demo/time-travel/reset"))).andExpect(status().isOk());
        assertThat(today(stage)).isEqualTo(realToday);
    }
}
