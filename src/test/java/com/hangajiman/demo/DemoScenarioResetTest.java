package com.hangajiman.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

// DEMO-03 시연 계정을 처음 세팅한 상태로 (resources/demo/seed-demo.sql)
@SpringBootTest(properties = "demo.time-travel.user-emails=demo@hangajiman.app")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class DemoScenarioResetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoClock clock;

    private User demo;

    @AfterEach
    void resetClock() {
        if (demo != null) {
            clock.resetUser(demo.getId());
        }
    }

    private User user(String email, String nickname) {
        User user = User.createLocal(email, "hash", nickname);
        user.completeOnboarding(nickname, clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    @Test
    void demoAccountResetsToSeededScenario() throws Exception {
        demo = user("demo@hangajiman.app", "시연" + System.nanoTime() % 100000);
        User friend = user("friend@hangajiman.app", "친구" + System.nanoTime() % 100000);
        User other = user("other@hangajiman.app", "다른" + System.nanoTime() % 100000);

        mockMvc.perform(as(demo, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.canResetDemo").value(true));
        mockMvc.perform(as(other, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.canResetDemo").value(false));
        mockMvc.perform(as(other, post("/api/v1/demo/reset-scenario")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("DEMO_NOT_ALLOWED"));

        // 시연 중 생긴 알림 (콕 찌르기 등)
        jdbcTemplate.update("INSERT INTO notifications (user_id, actor_id, type, target_id, message) VALUES (?, ?, 'NUDGE', 1, '콕')",
                friend.getId(), demo.getId());
        clock.travelUser(demo.getId(), Duration.ofDays(30));
        mockMvc.perform(as(demo, post("/api/v1/demo/reset-scenario"))).andExpect(status().isNoContent());
        mockMvc.perform(as(demo, post("/api/v1/demo/reset-scenario"))).andExpect(status().isNoContent()); // 여러 번 눌러도 같은 상태

        assertThat(clock.getUserOffset(demo.getId())).isEqualTo(Duration.ZERO);
        assertThat(count("SELECT COUNT(*) FROM wishes WHERE user_id = ? AND status = 'CLOSED'", demo.getId())).isEqualTo(18);
        assertThat(count("SELECT COUNT(*) FROM wishes WHERE user_id = ? AND status = 'ACTIVE'", demo.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM wishes WHERE user_id = ? AND status = 'ACTIVE'", friend.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM postcards WHERE user_id = ?", demo.getId())).isEqualTo(14);
        assertThat(count("SELECT COUNT(*) FROM year_reviews WHERE user_id = ?", demo.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", friend.getId())).isZero();
        // 돌돌이의 엽서·응원 2개·콕 찌르기
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ?", demo.getId())).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'NUDGE'", demo.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notifications n JOIN cheers c ON c.id = n.target_id WHERE n.user_id = ? AND n.type = 'CHEER'",
                demo.getId())).isEqualTo(2);
        mockMvc.perform(as(demo, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.stoneCount").value(12))
                .andExpect(jsonPath("$.data.dayIndex").value(13))
                .andExpect(jsonPath("$.data.stonedToday").value(false));
        // 내 정보 기록: 완성 돌탑 18개(첫 번째 소원 12 + 두 번째 소원 6), 방문 명소·엽서는 시드 기준
        mockMvc.perform(as(demo, get("/api/v1/users/me")))
                .andExpect(jsonPath("$.data.stats.towerCount").value(18))
                .andExpect(jsonPath("$.data.stats.visitedSpotCount").value(
                        count("SELECT COUNT(DISTINCT spot_id) FROM spot_visits WHERE user_id = ?", demo.getId())))
                .andExpect(jsonPath("$.data.stats.postcardCount").value(14));
    }
}
