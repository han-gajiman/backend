package com.hangajiman.spot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

// SPOT-03 방문 인증. 서버는 인증 결과만 받고 위치 정보는 받지도 저장하지도 않습니다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpotVisitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private Clock clock;

    private User onboardedUser() {
        User user = User.create(SocialProvider.KAKAO, "visit-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()));
    }

    private Long spotId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM spots WHERE name = ?", Long.class, name);
    }

    private Wish wishOf(User user, Long spotId) {
        return wishRepository.saveAndFlush(Wish.create(user.getId(), spotId, "소원", "실천", Visibility.FRIENDS,
                LocalDate.now(clock), 1));
    }

    private int visits(User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spot_visits WHERE user_id = ?", Integer.class, user.getId());
    }

    @Test
    void visitMarksSpotAndSecondVisitReturnsTheSameRecord() throws Exception {
        User user = onboardedUser();
        Long spot = spotId("호미곶 해맞이광장");

        String first = mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.visitId").isNumber())
                .andExpect(jsonPath("$.data.firstVisit").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(first).doesNotContain("distance", "latitude", "longitude");

        mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.firstVisit").value(false));
        assertThat(visits(user)).isEqualTo(1);

        mockMvc.perform(as(user, get("/api/v1/spots")))
                .andExpect(jsonPath("$.data[2].visited").value(true))
                .andExpect(jsonPath("$.data[2].visitedThisTower").value(true))
                .andExpect(jsonPath("$.data[2].radiusM").value(500))
                .andExpect(jsonPath("$.data[2].story").value(org.hamcrest.Matchers.startsWith("한반도를 호랑이에 빗대면")));
    }

    @Test
    void locationSentByMistakeIsNeverStored() throws Exception {
        User user = onboardedUser();

        mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spotId("동화사")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 35.99, \"longitude\": 128.70}"))
                .andExpect(status().isCreated());

        // 위치를 담을 컬럼 자체가 없습니다.
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'spot_visits'", String.class);
        assertThat(columns).doesNotContain("distance_m", "latitude", "longitude");
    }

    @Test
    void visitRequiresLoginAndExistingSpot() throws Exception {
        mockMvc.perform(post("/api/v1/spots/{id}/visits", spotId("동화사")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(as(onboardedUser(), post("/api/v1/spots/{id}/visits", 999_999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPOT_NOT_FOUND"));
    }

    @Test
    void visitedSpotUnlocksCurrentWishEvenIfVisitedBeforeTheWish() throws Exception {
        User user = onboardedUser();
        Long spot = spotId("영덕 해맞이공원");

        // 소원을 만들기 전에 먼저 방문
        mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spot))).andExpect(status().isCreated());
        wishOf(user, spot);

        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.spot.visited").value(true));
    }

    @Test
    void visitLinksCurrentWishOnlyWhenSpotMatches() throws Exception {
        User user = onboardedUser();
        Long wishSpot = spotId("부석사");
        Wish wish = wishOf(user, wishSpot);

        mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spotId("문무대왕릉")))).andExpect(status().isCreated());
        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.spot.visited").value(false));

        mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", wishSpot))).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("SELECT wish_id FROM spot_visits WHERE user_id = ? AND spot_id = ?",
                Long.class, user.getId(), wishSpot)).isEqualTo(wish.getId());
        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.spot.visited").value(true));
    }
}
