package com.hangajiman.wish.controller;

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

// TOWER-02 완성 회고, TOWER-03 모아보기. 실제 DB(hangajiman_test), 테스트마다 롤백
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReflectionAndCollectionTest {

    private static final String ACHIEVED = "{\"result\": \"ACHIEVED\", \"reflection\": \"한 달 동안 단어 600개 외움\"}";

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

    private User onboardedUser(LocalDate journeyStartedOn) {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        user.startJourneyIfFirst(journeyStartedOn);
        return userRepository.saveAndFlush(user);
    }

    private Wish wish(User user, long spotId, LocalDate start, String status) {
        Wish wish = wishRepository.saveAndFlush(Wish.create(user.getId(), spotId, "소원 " + start, "실천",
                Visibility.PRIVATE, start, user.roundOf(Wish.endDateOf(start))));
        if (!"ACTIVE".equals(status)) {
            jdbcTemplate.update("UPDATE wishes SET status = ? WHERE id = ?", status, wish.getId());
            entityManager.clear();
        }
        return wish;
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String reflectionUrl(Wish wish) {
        return "/api/v1/wishes/" + wish.getId() + "/reflection";
    }

    // --- TOWER-02 완성 회고 ---

    @Test
    void reflectCompletedTowerThenStartNewWish() throws Exception {
        User user = onboardedUser(today().minusDays(30));
        Wish completed = wish(user, 2, today().minusDays(30), "COMPLETED");

        mockMvc.perform(as(user, post(reflectionUrl(completed))).content(ACHIEVED))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(user, get("/api/v1/wishes/current"))).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(as(user, post("/api/v1/wishes"))
                        .content("{\"content\": \"새 소원\", \"actionGoal\": \"새 실천\", \"spotId\": 1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void reflectionTextIsOptional() throws Exception {
        User user = onboardedUser(today().minusDays(30));
        Wish completed = wish(user, 2, today().minusDays(30), "COMPLETED");

        mockMvc.perform(as(user, post(reflectionUrl(completed))).content("{\"result\": \"NOT_ACHIEVED\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotReflectBefore30Days() throws Exception {
        User user = onboardedUser(today());
        Wish active = wish(user, 2, today().minusDays(10), "ACTIVE");

        mockMvc.perform(as(user, post(reflectionUrl(active))).content(ACHIEVED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TOWER_NOT_COMPLETED"));
    }

    @Test
    void canReflectExpiredTowerEvenIfSchedulerHasNotRun() throws Exception {
        User user = onboardedUser(today().minusDays(31));
        Wish expired = wish(user, 2, today().minusDays(31), "ACTIVE");

        mockMvc.perform(as(user, post(reflectionUrl(expired))).content(ACHIEVED))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotReflectTwice() throws Exception {
        User user = onboardedUser(today().minusDays(30));
        Wish completed = wish(user, 2, today().minusDays(30), "COMPLETED");
        mockMvc.perform(as(user, post(reflectionUrl(completed))).content(ACHIEVED)).andExpect(status().isNoContent());

        mockMvc.perform(as(user, post(reflectionUrl(completed))).content(ACHIEVED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("이미 회고를 남긴 돌탑이에요."));
    }

    @Test
    void cannotReflectOthersTower() throws Exception {
        User owner = onboardedUser(today().minusDays(30));
        User other = onboardedUser(today());
        Wish completed = wish(owner, 2, today().minusDays(30), "COMPLETED");

        mockMvc.perform(as(other, post(reflectionUrl(completed))).content(ACHIEVED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_OWNER"));
    }

    @Test
    void invalidReflectionInput() throws Exception {
        User user = onboardedUser(today().minusDays(30));
        Wish completed = wish(user, 2, today().minusDays(30), "COMPLETED");

        mockMvc.perform(as(user, post(reflectionUrl(completed))).content("{\"reflection\": \"결과 없음\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post(reflectionUrl(completed)))
                        .content("{\"result\": \"ACHIEVED\", \"reflection\": \"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post(reflectionUrl(completed))).content("{\"result\": \"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- TOWER-03 모아보기 ---

    @Test
    void collectionOfCurrentRound() throws Exception {
        User user = onboardedUser(today().minusDays(100));
        Wish first = wish(user, 3, today().minusDays(100), "CLOSED");
        jdbcTemplate.update("UPDATE wishes SET result = 'ACHIEVED', reflection = '좋았다' WHERE id = ?", first.getId());
        Wish second = wish(user, 2, today().minusDays(60), "COMPLETED"); // 회고 전 (진행 중 돌탑은 1인 1개라 ACTIVE는 따로 확인)
        for (int i = 0; i < 3; i++) {
            stoneRepository.save(Stone.of(first.getId(), today().minusDays(100 - i), null));
        }
        stoneRepository.saveAndFlush(Stone.of(second.getId(), today().minusDays(60), "기록"));

        mockMvc.perform(as(user, get("/api/v1/wishes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].wishId", contains(first.getId().intValue(), second.getId().intValue())))
                .andExpect(jsonPath("$.data[0].spotId").isNumber())
                .andExpect(jsonPath("$.data[0].spotName").value("호미곶 해맞이광장"))
                .andExpect(jsonPath("$.data[0].stoneCount").value(3))
                .andExpect(jsonPath("$.data[0].result").value("ACHIEVED"))
                .andExpect(jsonPath("$.data[0].reflection").value("좋았다"))
                .andExpect(jsonPath("$.data[0].endDate").value(today().minusDays(71).toString()))
                .andExpect(jsonPath("$.data[0].visited").value(false))
                .andExpect(jsonPath("$.data[0].startDate").value(today().minusDays(100).toString()))
                .andExpect(jsonPath("$.data[1].stoneCount").value(1))
                .andExpect(jsonPath("$.data[1].result").isEmpty())
                .andExpect(jsonPath("$.data[1].reflection").isEmpty());
    }

    @Test
    void activeTowerIsNotInCollection() throws Exception {
        User user = onboardedUser(today().minusDays(100));
        Wish closed = wish(user, 3, today().minusDays(100), "CLOSED");
        wish(user, 1, today().minusDays(5), "ACTIVE");

        mockMvc.perform(as(user, get("/api/v1/wishes")))
                .andExpect(jsonPath("$.data[*].wishId", contains(closed.getId().intValue())));
    }

    @Test
    void collectionByRound() throws Exception {
        User user = onboardedUser(today().minusDays(400));
        Wish round1 = wish(user, 1, today().minusDays(400), "CLOSED"); // 끝난 날이 371일 전 → 1회차
        Wish round2 = wish(user, 2, today().minusDays(40), "CLOSED");  // 끝난 날이 11일 전 → 2회차

        mockMvc.perform(as(user, get("/api/v1/wishes").param("round", "1")))
                .andExpect(jsonPath("$.data[*].wishId", contains(round1.getId().intValue())));
        mockMvc.perform(as(user, get("/api/v1/wishes").param("round", "2")))
                .andExpect(jsonPath("$.data[*].wishId", contains(round2.getId().intValue())));
        mockMvc.perform(as(user, get("/api/v1/wishes"))) // 생략 시 현재 회차(2)
                .andExpect(jsonPath("$.data[*].wishId", contains(round2.getId().intValue())));
        mockMvc.perform(as(user, get("/api/v1/wishes").param("round", "3")))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void emptyCollectionAndInvalidRound() throws Exception {
        User user = onboardedUser(today());

        mockMvc.perform(as(user, get("/api/v1/wishes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(as(user, get("/api/v1/wishes").param("round", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void collectionShowsOnlyMyTowers() throws Exception {
        User me = onboardedUser(today().minusDays(40));
        User other = onboardedUser(today().minusDays(40));
        wish(other, 2, today().minusDays(40), "CLOSED");

        mockMvc.perform(as(me, get("/api/v1/wishes"))).andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- TOWER-04 1년 회고 ---

    private String reviewUrl(int round) {
        return "/api/v1/wishes/rounds/" + round + "/review";
    }

    @Test
    void roundsListsTowerRoundsAndCurrent() throws Exception {
        User user = onboardedUser(today().minusDays(400));
        wish(user, 1, today().minusDays(400), "CLOSED"); // 1회차
        wish(user, 2, today().minusDays(64), "COMPLETED"); // 끝난 날이 시작 365일째 → 2회차

        mockMvc.perform(as(user, get("/api/v1/wishes/rounds")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].round").value(1))
                .andExpect(jsonPath("$.data[0].towerCount").value(1))
                .andExpect(jsonPath("$.data[0].reviewAt").value(today().minusDays(35).toString()))
                .andExpect(jsonPath("$.data[0].canWriteReview").value(true))
                .andExpect(jsonPath("$.data[0].review").isEmpty())
                .andExpect(jsonPath("$.data[1].round").value(2))
                .andExpect(jsonPath("$.data[1].towerCount").value(1))
                .andExpect(jsonPath("$.data[1].canWriteReview").value(false));
    }

    @Test
    void newUserHasOnlyFirstRound() throws Exception {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding("소원지기", clock.instant());
        userRepository.saveAndFlush(user);

        mockMvc.perform(as(user, get("/api/v1/wishes/rounds")))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].round").value(1))
                .andExpect(jsonPath("$.data[0].towerCount").value(0))
                .andExpect(jsonPath("$.data[0].reviewAt").isEmpty())
                .andExpect(jsonPath("$.data[0].canWriteReview").value(false));
    }

    @Test
    void writeYearReviewOnceWithFewerThan12Towers() throws Exception {
        User user = onboardedUser(today().minusDays(400));
        wish(user, 1, today().minusDays(400), "CLOSED");

        mockMvc.perform(as(user, post(reviewUrl(1))).content("{\"content\": \" 끝까지 하는 사람 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.round").value(1))
                .andExpect(jsonPath("$.data.content").value("끝까지 하는 사람"));

        mockMvc.perform(as(user, get("/api/v1/wishes/rounds")))
                .andExpect(jsonPath("$.data[0].review").value("끝까지 하는 사람"))
                .andExpect(jsonPath("$.data[0].canWriteReview").value(false));

        mockMvc.perform(as(user, post(reviewUrl(1))).content("{\"content\": \"다시 쓰기\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("YEAR_REVIEW_ALREADY"));
    }

    @Test
    void cannotWriteReviewBeforeYearPassesOrWithoutTowers() throws Exception {
        User user = onboardedUser(today().minusDays(800)); // 지금 3회차
        wish(user, 1, today().minusDays(800), "CLOSED"); // 1회차
        wish(user, 2, today().minusDays(60), "CLOSED"); // 3회차
        String body = "{\"content\": \"회고\"}";

        for (int round : new int[] {2, 3, 4}) { // 돌탑 없는 회차, 현재 회차, 아직 안 온 회차
            mockMvc.perform(as(user, post(reviewUrl(round))).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("YEAR_REVIEW_NOT_AVAILABLE"));
        }
    }

    @Test
    void yearReviewOn364thAndNext() throws Exception {
        User justBefore = onboardedUser(today().minusDays(364));
        wish(justBefore, 1, today().minusDays(364), "CLOSED");
        User justAfter = onboardedUser(today().minusDays(365));
        wish(justAfter, 1, today().minusDays(365), "CLOSED");
        String body = "{\"content\": \"회고\"}";

        mockMvc.perform(as(justBefore, post(reviewUrl(1))).content(body)).andExpect(status().isBadRequest());
        mockMvc.perform(as(justAfter, post(reviewUrl(1))).content(body)).andExpect(status().isCreated());
    }

    @Test
    void yearReviewLengthIsChecked() throws Exception {
        User user = onboardedUser(today().minusDays(400));
        wish(user, 1, today().minusDays(400), "CLOSED");

        mockMvc.perform(as(user, post(reviewUrl(1))).content("{\"content\": \"" + "가".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(as(user, post(reviewUrl(1))).content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user, post(reviewUrl(1))).content("{\"content\": \"" + "가".repeat(200) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void yearReviewIsPerUser() throws Exception {
        User me = onboardedUser(today().minusDays(400));
        User other = onboardedUser(today().minusDays(400));
        wish(me, 1, today().minusDays(400), "CLOSED");
        wish(other, 1, today().minusDays(400), "CLOSED");

        mockMvc.perform(as(other, post(reviewUrl(1))).content("{\"content\": \"다른 사람 회고\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(as(me, get("/api/v1/wishes/rounds")))
                .andExpect(jsonPath("$.data[0].review").isEmpty())
                .andExpect(jsonPath("$.data[0].canWriteReview").value(true));
    }

    private void visit(User user, long spotId, LocalDate on) {
        jdbcTemplate.update("INSERT INTO spot_visits (user_id, spot_id, verified_at) VALUES (?, ?, ?)",
                user.getId(), spotId, java.sql.Timestamp.from(on.atTime(12, 0).atZone(clock.getZone()).toInstant()));
    }

    @Test
    void roundsIncludeVisitsOfThatRoundOnly() throws Exception {
        User user = onboardedUser(today().minusDays(400));
        User other = onboardedUser(today().minusDays(400));
        wish(user, 1, today().minusDays(400), "CLOSED");
        visit(user, 3, today().minusDays(300)); // 1회차
        visit(user, 8, today().minusDays(200)); // 1회차
        visit(user, 2, today().minusDays(10)); // 2회차
        visit(other, 5, today().minusDays(300));

        mockMvc.perform(as(user, get("/api/v1/wishes/rounds")))
                .andExpect(jsonPath("$.data[0].visits.length()").value(2))
                .andExpect(jsonPath("$.data[0].visits[0].spotId").value(3))
                .andExpect(jsonPath("$.data[0].visits[0].spotName").value("호미곶 해맞이광장"))
                .andExpect(jsonPath("$.data[0].visits[0].visitedOn").value(today().minusDays(300).toString()))
                .andExpect(jsonPath("$.data[0].visits[1].spotId").value(8))
                .andExpect(jsonPath("$.data[1].visits.length()").value(1))
                .andExpect(jsonPath("$.data[1].visits[0].spotId").value(2));
    }

    @Test
    void roundsWithoutVisitsHaveEmptyList() throws Exception {
        mockMvc.perform(as(onboardedUser(today()), get("/api/v1/wishes/rounds")))
                .andExpect(jsonPath("$.data[0].visits.length()").value(0));
    }
}
