package com.hangajiman.spot.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

// 실제 DB(hangajiman_test)의 시드 명소 10곳으로 확인합니다.
@SpringBootTest
@AutoConfigureMockMvc
class SpotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void getAllSpots() throws Exception {
        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(10)))
                .andExpect(jsonPath("$.data[0].name").value("팔공산 갓바위"))
                .andExpect(jsonPath("$.data[1].category").value("PASS"))
                .andExpect(jsonPath("$.data[1].latitude").value(36.76844))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void filterByCategory() throws Exception {
        mockMvc.perform(get("/api/v1/spots").param("category", "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("문경새재 과거길"));
    }

    @Test
    void unknownCategoryReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/spots").param("category", "NONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Transactional
    void visitedIsMarkedOnlyForTheVisitor() throws Exception {
        User visitor = onboardedUser("spot-visitor");
        User other = onboardedUser("spot-other");
        Long spotId = jdbcTemplate.queryForObject("SELECT id FROM spots WHERE name = '문경새재 과거길'", Long.class);
        jdbcTemplate.update("INSERT INTO spot_visits (user_id, spot_id, distance_m) VALUES (?, ?, 0)", visitor.getId(), spotId);

        // 로그인 전
        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(jsonPath("$.data[1].visited").value(false));
        // 방문한 회원
        mockMvc.perform(get("/api/v1/spots").header(HttpHeaders.AUTHORIZATION, bearer(visitor)))
                .andExpect(jsonPath("$.data[0].visited").value(false))
                .andExpect(jsonPath("$.data[1].visited").value(true));
        // 다른 회원
        mockMvc.perform(get("/api/v1/spots").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.data[1].visited").value(false));
    }

    private User onboardedUser(String providerUserId) {
        User user = User.create(SocialProvider.KAKAO, providerUserId, null);
        user.completeOnboarding("소원지기", Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }
}
