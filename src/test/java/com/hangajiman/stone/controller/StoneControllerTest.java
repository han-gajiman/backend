package com.hangajiman.stone.controller;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 실제 DB(hangajiman_test). 테스트마다 롤백됩니다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoneControllerTest {

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
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private User onboardedUser() {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private Wish wishOf(User user, LocalDate start, Visibility visibility) {
        return wishRepository.saveAndFlush(Wish.create(user.getId(), 2L, "소원", "단어 20개", visibility, start, 1));
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String stonesUrl(Wish wish) {
        return "/api/v1/wishes/" + wish.getId() + "/stones";
    }

    @Test
    void attendanceWithoutBody() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today(), Visibility.PRIVATE);

        mockMvc.perform(as(user, post(stonesUrl(wish))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stoneId").isNumber())
                .andExpect(jsonPath("$.data.stoneDate").value(today().toString()))
                .andExpect(jsonPath("$.data.type").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.stoneCount").value(1));

        mockMvc.perform(as(user, get("/api/v1/wishes/current")))
                .andExpect(jsonPath("$.data.stonedToday").value(true))
                .andExpect(jsonPath("$.data.stoneCount").value(1));
    }

    @Test
    void practiceWithContent() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today().minusDays(3), Visibility.PRIVATE);
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), today().minusDays(1), null));

        mockMvc.perform(as(user, post(stonesUrl(wish))).content("{\"content\": \"영어 단어 20개 외움\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("PRACTICE"))
                .andExpect(jsonPath("$.data.stoneCount").value(2));
    }

    @Test
    void blankContentIsAttendance() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today(), Visibility.PRIVATE);

        mockMvc.perform(as(user, post(stonesUrl(wish))).content("{\"content\": \"  \"}"))
                .andExpect(jsonPath("$.data.type").value("ATTENDANCE"));
    }

    @Test
    void onlyOneStonePerDay() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today(), Visibility.PRIVATE);
        mockMvc.perform(as(user, post(stonesUrl(wish)))).andExpect(status().isCreated());

        mockMvc.perform(as(user, post(stonesUrl(wish))).content("{\"content\": \"또\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STONE_ALREADY_TODAY"));
    }

    @Test
    void cannotStackOnOthersOrMissingWish() throws Exception {
        User owner = onboardedUser();
        User other = onboardedUser();
        Wish wish = wishOf(owner, today(), Visibility.FRIENDS);

        mockMvc.perform(as(other, post(stonesUrl(wish))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_OWNER"));
        mockMvc.perform(as(other, post("/api/v1/wishes/999999/stones")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_FOUND"));
    }

    @Test
    void cannotStackOnCompletedWish() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today().minusDays(30), Visibility.PRIVATE);
        jdbcTemplate.update("UPDATE wishes SET status = 'COMPLETED' WHERE id = ?", wish.getId());
        entityManager.clear();

        mockMvc.perform(as(user, post(stonesUrl(wish))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_ACTIVE"));
    }

    @Test
    void cannotStackAfterEndDateEvenIfSchedulerHasNotRun() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today().minusDays(30), Visibility.PRIVATE); // 어제가 30일째, 상태는 아직 ACTIVE

        mockMvc.perform(as(user, post(stonesUrl(wish))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_ACTIVE"));
    }

    @Test
    void canStackOnDay30() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today().minusDays(29), Visibility.PRIVATE); // 오늘이 30일째

        mockMvc.perform(as(user, post(stonesUrl(wish)))).andExpect(status().isCreated());
    }

    @Test
    void tooLongContentIsRejected() throws Exception {
        User user = onboardedUser();
        Wish wish = wishOf(user, today(), Visibility.PRIVATE);

        mockMvc.perform(as(user, post(stonesUrl(wish))).content("{\"content\": \"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void listIsLatestFirstWithPaging() throws Exception {
        User user = onboardedUser();
        LocalDate start = today().minusDays(2);
        Wish wish = wishOf(user, start, Visibility.PRIVATE);
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), start, null));
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), start.plusDays(1), "둘째 날"));
        stoneRepository.saveAndFlush(Stone.of(wish.getId(), start.plusDays(2), null));

        mockMvc.perform(as(user, get(stonesUrl(wish))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].stoneDate", contains(
                        start.plusDays(2).toString(), start.plusDays(1).toString(), start.toString())))
                .andExpect(jsonPath("$.data.content[1].type").value("PRACTICE"))
                .andExpect(jsonPath("$.data.content[1].content").value("둘째 날"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(as(user, get(stonesUrl(wish))).param("size", "2"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(as(user, get(stonesUrl(wish))).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(as(user, get(stonesUrl(wish))).param("size", "100"))
                .andExpect(jsonPath("$.data.size").value(50)); // 최대 50
    }

    @Test
    void friendCanViewFriendsVisibilityOnly() throws Exception {
        User owner = onboardedUser();
        User friend = onboardedUser();
        User stranger = onboardedUser();
        jdbcTemplate.update("INSERT INTO friendships (user_id, friend_id) VALUES (?, ?), (?, ?)",
                owner.getId(), friend.getId(), friend.getId(), owner.getId());
        Wish friendsWish = wishOf(owner, today(), Visibility.FRIENDS);

        mockMvc.perform(as(friend, get(stonesUrl(friendsWish)))).andExpect(status().isOk());
        mockMvc.perform(as(stranger, get(stonesUrl(friendsWish))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_OWNER"));

        jdbcTemplate.update("UPDATE wishes SET visibility = 'PRIVATE' WHERE id = ?", friendsWish.getId());
        entityManager.clear();
        mockMvc.perform(as(friend, get(stonesUrl(friendsWish)))).andExpect(status().isForbidden());
    }
}
