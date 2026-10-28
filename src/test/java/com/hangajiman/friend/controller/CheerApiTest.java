package com.hangajiman.friend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

// FRIEND-03 응원 작성·목록
class CheerApiTest extends FriendApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private static String body(String message) {
        return "{\"message\": \"" + message + "\"}";
    }

    private List<Map<String, Object>> notificationsOf(User user) {
        return jdbcTemplate.queryForList(
                "SELECT type, actor_id, target_id, message, is_read FROM notifications WHERE user_id = ?", user.getId());
    }

    @Test
    void friendCheersAndOwnerGetsNotification() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.FRIENDS);

        String response = mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId()))
                        .content(body("  오늘도 하나 쌓자!  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cheerId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long cheerId = ((Number) JsonPath.read(response, "$.data.cheerId")).longValue();

        List<Map<String, Object>> notifications = notificationsOf(owner);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0))
                .containsEntry("type", "CHEER")
                .containsEntry("actor_id", friend.getId())
                .containsEntry("target_id", cheerId)
                .containsEntry("message", "지기님이 응원을 보냈어요.")
                .containsEntry("is_read", false);

        // 앞뒤 공백은 지우고 저장
        mockMvc.perform(as(owner, get("/api/v1/wishes/{id}/cheers", wish.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].cheerId").value(cheerId))
                .andExpect(jsonPath("$.data.content[0].writerNickname").value("지기"))
                .andExpect(jsonPath("$.data.content[0].isAnonymous").value(false))
                .andExpect(jsonPath("$.data.content[0].message").value("오늘도 하나 쌓자!"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value(matchesPattern(".*\\+09:00")))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void cheersAreNewestFirstAndFriendsCanRead() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.FRIENDS);
        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("첫 응원")));
        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("두 번째 응원")));

        mockMvc.perform(as(friend, get("/api/v1/wishes/{id}/cheers", wish.getId()).param("size", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].message").value("두 번째 응원"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void exactly100CharsIsOkAnd101Fails() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.FRIENDS);

        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("가".repeat(100))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHEER_TOO_LONG"));
    }

    @Test
    void blankMessageFails() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.FRIENDS);

        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void notFriendCannotCheer() throws Exception {
        User owner = user("석현");
        Wish wish = activeWish(owner, Visibility.FRIENDS);

        mockMvc.perform(as(user("남"), post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("화이팅")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHEER_NOT_ALLOWED"));
    }

    @Test
    void privateWishCannotBeCheeredOrRead() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.PRIVATE);

        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("화이팅")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHEER_NOT_ALLOWED"));
        mockMvc.perform(as(friend, get("/api/v1/wishes/{id}/cheers", wish.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHEER_NOT_ALLOWED"));

        // 주인은 비공개여도 볼 수 있음
        mockMvc.perform(as(owner, get("/api/v1/wishes/{id}/cheers", wish.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void ownerCannotCheerOwnWish() throws Exception {
        User owner = user("석현");
        Wish wish = activeWish(owner, Visibility.FRIENDS);

        mockMvc.perform(as(owner, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("나 화이팅")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHEER_NOT_ALLOWED"));
    }

    @Test
    void finishedWishCannotBeCheered() throws Exception {
        User owner = user("석현");
        User friend = user("지기");
        befriend(owner, friend);
        Wish wish = activeWish(owner, Visibility.FRIENDS);
        jdbcTemplate.update("UPDATE wishes SET status = 'COMPLETED' WHERE id = ?", wish.getId());
        entityManager.clear(); // JDBC로 바꾼 상태를 다시 읽도록

        mockMvc.perform(as(friend, post("/api/v1/wishes/{id}/cheers", wish.getId())).content(body("화이팅")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_ACTIVE"));
    }

    @Test
    void unknownWishFails() throws Exception {
        mockMvc.perform(as(user("지기"), post("/api/v1/wishes/{id}/cheers", Long.MAX_VALUE)).content(body("화이팅")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WISH_NOT_FOUND"));
    }
}
