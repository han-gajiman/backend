package com.hangajiman.wish.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.repository.WishRepository;

// WISH-04 공개 범위 변경. 실제 DB(hangajiman_test), 테스트마다 롤백
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VisibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WishRepository wishRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private User onboardedUser(String nickname) {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding(nickname, clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String url(Wish wish) {
        return "/api/v1/wishes/" + wish.getId() + "/visibility";
    }

    @Test
    void ownerCanSwitchAndFriendSeesIt() throws Exception {
        User owner = onboardedUser("주인");
        User friend = onboardedUser("친구");
        jdbcTemplate.update("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)",
                owner.getId(), friend.getId(), friend.getId(), owner.getId());
        Wish wish = wishRepository.saveAndFlush(Wish.create(owner.getId(), 2L, "소원", "실천",
                Visibility.PRIVATE, LocalDate.now(clock), 1));

        mockMvc.perform(as(friend, get("/api/v1/friends")))
                .andExpect(jsonPath("$.data.content[0].wish").isEmpty());

        mockMvc.perform(as(owner, patch(url(wish))).content("{\"visibility\": \"FRIENDS\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(owner, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.visibility").value("FRIENDS"));
        mockMvc.perform(as(friend, get("/api/v1/friends")))
                .andExpect(jsonPath("$.data.content[0].wish.content").value("소원"));

        mockMvc.perform(as(owner, patch(url(wish))).content("{\"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(friend, get("/api/v1/friends")))
                .andExpect(jsonPath("$.data.content[0].wish").isEmpty());
    }

    @Test
    void othersCannotChange() throws Exception {
        User owner = onboardedUser("주인");
        User other = onboardedUser("남");
        Wish wish = wishRepository.saveAndFlush(Wish.create(owner.getId(), 2L, "소원", "실천",
                Visibility.PRIVATE, LocalDate.now(clock), 1));

        mockMvc.perform(as(other, patch(url(wish))).content("{\"visibility\": \"FRIENDS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_OWNER"));
    }

    @Test
    void invalidVisibilityIsRejected() throws Exception {
        User owner = onboardedUser("주인");
        Wish wish = wishRepository.saveAndFlush(Wish.create(owner.getId(), 2L, "소원", "실천",
                Visibility.PRIVATE, LocalDate.now(clock), 1));

        mockMvc.perform(as(owner, patch(url(wish))).content("{\"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(owner, patch(url(wish))).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
