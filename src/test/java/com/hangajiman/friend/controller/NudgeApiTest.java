package com.hangajiman.friend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;

// FRIEND-04 콕 찌르기
class NudgeApiTest extends FriendApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Map<String, Object>> nudgesTo(User user) {
        return jdbcTemplate.queryForList(
                "SELECT actor_id, target_id, message, is_read FROM notifications WHERE user_id = ? AND type = 'NUDGE'",
                user.getId());
    }

    @Test
    void nudgeFriendWhoHasNotStonedToday() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        Wish wish = activeWish(friend, Visibility.FRIENDS);
        stone(wish, today().minusDays(1)); // 어제는 쌓았지만 오늘은 아직

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", friend.getId())))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> nudges = nudgesTo(friend);
        assertThat(nudges).hasSize(1);
        assertThat(nudges.get(0))
                .containsEntry("actor_id", me.getId())
                .containsEntry("target_id", wish.getId())
                .containsEntry("message", "석현님이 콕 찔렀어요! 오늘의 돌을 쌓아 보세요.")
                .containsEntry("is_read", false);
    }

    @Test
    void secondNudgeTodayIsLimited() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        activeWish(friend, Visibility.FRIENDS);
        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", friend.getId())))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", friend.getId())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("NUDGE_LIMIT"));
        assertThat(nudgesTo(friend)).hasSize(1);
    }

    @Test
    void otherFriendCanAlsoNudgeSamePerson() throws Exception {
        User friend = user("지기");
        User a = user("A");
        User b = user("B");
        befriend(a, friend);
        befriend(b, friend);
        activeWish(friend, Visibility.FRIENDS);

        mockMvc.perform(as(a, post("/api/v1/friends/{id}/nudge", friend.getId()))).andExpect(status().isNoContent());
        mockMvc.perform(as(b, post("/api/v1/friends/{id}/nudge", friend.getId()))).andExpect(status().isNoContent());
        assertThat(nudgesTo(friend)).hasSize(2);
    }

    @Test
    void yesterdaysNudgeDoesNotCount() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        Wish wish = activeWish(friend, Visibility.FRIENDS);
        Instant yesterdayNight = today().atStartOfDay(ClockConfig.KST).toInstant().minusSeconds(60);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, actor_id, type, target_id, message, created_at, updated_at)
                VALUES (?, ?, 'NUDGE', ?, '어제 콕', ?, ?)
                """, friend.getId(), me.getId(), wish.getId(),
                Timestamp.from(yesterdayNight), Timestamp.from(yesterdayNight));

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", friend.getId())))
                .andExpect(status().isNoContent());
    }

    @Test
    void friendWhoAlreadyStonedTodayCannotBeNudged() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        Wish wish = activeWish(friend, Visibility.FRIENDS);
        stone(wish, today());

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", friend.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NUDGE_NOT_ALLOWED"));
    }

    @Test
    void friendWithoutPublicActiveWishCannotBeNudged() throws Exception {
        User me = user("석현");
        User noWish = user("돌탑없음");
        User privateWish = user("비공개");
        befriend(me, noWish);
        befriend(me, privateWish);
        activeWish(privateWish, Visibility.PRIVATE);

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", noWish.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NUDGE_NOT_ALLOWED"));
        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", privateWish.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NUDGE_NOT_ALLOWED"));
    }

    @Test
    void notFriendOrSelfFails() throws Exception {
        User me = user("석현");
        User stranger = user("남");
        activeWish(stranger, Visibility.FRIENDS);

        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", stranger.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_NOT_FOUND"));
        mockMvc.perform(as(me, post("/api/v1/friends/{id}/nudge", me.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_NOT_FOUND"));
    }
}
