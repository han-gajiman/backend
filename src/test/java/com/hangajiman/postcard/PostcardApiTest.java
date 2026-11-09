package com.hangajiman.postcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.jayway.jsonpath.JsonPath;

// SPOT-04 엽서: 인증마다 아직 없는 1장, 같은 명소는 돌탑마다 한 번, 가진 엽서만 친구·나에게 보내기
class PostcardApiTest extends FriendApiTestSupport {

    private static final long HOMIGOT = 3L; // V2 시드 호미곶

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String visit(User user, long spotId) throws Exception {
        return mockMvc.perform(as(user, post("/api/v1/spots/{id}/visits", spotId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // 지금 돌탑을 끝내고 새 돌탑 시작
    private Wish nextTower(User user, Wish current) {
        jdbcTemplate.update("UPDATE wishes SET status = 'CLOSED' WHERE id = ?", current.getId());
        return activeWish(user, Visibility.PRIVATE);
    }

    private String letter(long spotId, int style, Long receiverId, String message) {
        return "{\"spotId\": " + spotId + ", \"style\": " + style
                + (receiverId == null ? "" : ", \"receiverId\": " + receiverId) + ", \"message\": \"" + message + "\"}";
    }

    private int ownedStyle(User user, long spotId) {
        return jdbcTemplate.queryForObject("SELECT style FROM postcards WHERE user_id = ? AND spot_id = ? LIMIT 1",
                Integer.class, user.getId(), spotId);
    }

    @Test
    void visitGivesOnePostcardAndSameTowerCannotVisitAgain() throws Exception {
        User user = user("소원지기");
        activeWish(user, Visibility.PRIVATE);

        String first = visit(user, HOMIGOT);
        assertThat((Boolean) JsonPath.read(first, "$.data.firstVisit")).isTrue();
        assertThat((Integer) JsonPath.read(first, "$.data.postcard.style")).isBetween(1, 4);
        assertThat((String) JsonPath.read(first, "$.data.postcard.spotName")).isEqualTo("호미곶 해맞이광장");

        String again = visit(user, HOMIGOT);
        assertThat((Boolean) JsonPath.read(again, "$.data.firstVisit")).isFalse();
        assertThat((Object) JsonPath.read(again, "$.data.postcard")).isNull();

        mockMvc.perform(as(user, get("/api/v1/postcards")))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(as(user, get("/api/v1/spots")))
                .andExpect(jsonPath("$.data[2].visited").value(true))
                .andExpect(jsonPath("$.data[2].visitedThisTower").value(true));
    }

    @Test
    void nextTowerCanVisitAgainUntilAllFourCollected() throws Exception {
        User user = user("소원지기");
        Wish tower = activeWish(user, Visibility.PRIVATE);
        Set<Integer> styles = new HashSet<>();

        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                tower = nextTower(user, tower);
                mockMvc.perform(as(user, get("/api/v1/spots")))
                        .andExpect(jsonPath("$.data[2].visitedThisTower").value(false));
            }
            styles.add(JsonPath.read(visit(user, HOMIGOT), "$.data.postcard.style"));
        }
        assertThat(styles).containsExactlyInAnyOrder(1, 2, 3, 4); // 매번 아직 없는 엽서

        nextTower(user, tower);
        String fifth = visit(user, HOMIGOT);
        assertThat((Boolean) JsonPath.read(fifth, "$.data.firstVisit")).isTrue();
        assertThat((Object) JsonPath.read(fifth, "$.data.postcard")).isNull(); // 4종을 다 모음
        mockMvc.perform(as(user, get("/api/v1/postcards"))).andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void sendToFriendNotifiesAndShowsInMailbox() throws Exception {
        User me = user("돌쌓기");
        User friend = user("지기");
        befriend(me, friend);
        visit(me, HOMIGOT);
        int style = ownedStyle(me, HOMIGOT);

        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, style, friend.getId(), " 호미곶에서 안부 전해 ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.letterId").isNumber());

        mockMvc.perform(as(friend, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.hasUnreadLetter").value(true));
        String received = mockMvc.perform(as(friend, get("/api/v1/postcards/letters/received")))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].spotName").value("호미곶 해맞이광장"))
                .andExpect(jsonPath("$.data.content[0].style").value(style))
                .andExpect(jsonPath("$.data.content[0].message").value("호미곶에서 안부 전해"))
                .andExpect(jsonPath("$.data.content[0].fromNickname").value("돌쌓기"))
                .andExpect(jsonPath("$.data.content[0].toNickname").value("지기"))
                .andExpect(jsonPath("$.data.content[0].fromMe").value(false))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'POSTCARD'", Integer.class, friend.getId()))
                .isEqualTo(1);

        // 보내도 내 엽서는 그대로
        mockMvc.perform(as(me, get("/api/v1/postcards"))).andExpect(jsonPath("$.data.length()").value(1));

        // 보낸 엽서: 내 보낸함에만 있고, 받은 친구의 보낸함에는 없음
        mockMvc.perform(as(me, get("/api/v1/postcards/letters/sent")))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].toNickname").value("지기"))
                .andExpect(jsonPath("$.data.content[0].fromMe").value(true))
                .andExpect(jsonPath("$.data.content[0].message").value("호미곶에서 안부 전해"));
        mockMvc.perform(as(friend, get("/api/v1/postcards/letters/sent")))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(as(me, get("/api/v1/postcards/letters/received")))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        Integer letterId = JsonPath.read(received, "$.data.content[0].letterId");
        mockMvc.perform(as(me, patch("/api/v1/postcards/letters/{id}/read", letterId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LETTER_NOT_FOUND"));
        mockMvc.perform(as(friend, patch("/api/v1/postcards/letters/{id}/read", letterId))).andExpect(status().isNoContent());
        mockMvc.perform(as(friend, get("/api/v1/users/me"))).andExpect(jsonPath("$.data.hasUnreadLetter").value(false));
    }

    @Test
    void sendToMe() throws Exception {
        User me = user("돌쌓기");
        visit(me, HOMIGOT);

        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, ownedStyle(me, HOMIGOT), null, "1년 뒤의 나에게")))
                .andExpect(status().isCreated());

        mockMvc.perform(as(me, get("/api/v1/postcards/letters/received")))
                .andExpect(jsonPath("$.data.content[0].fromMe").value(true))
                .andExpect(jsonPath("$.data.content[0].fromNickname").value("돌쌓기"))
                .andExpect(jsonPath("$.data.content[0].toNickname").value("돌쌓기"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'POSTCARD'", Integer.class, me.getId()))
                .isZero();
    }

    @Test
    void onlyOwnedPostcardToFriendWithinLimit() throws Exception {
        User me = user("돌쌓기");
        User stranger = user("모르는사람");
        visit(me, HOMIGOT);
        int style = ownedStyle(me, HOMIGOT);
        int notOwned = style % 4 + 1;

        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, notOwned, null, "안녕")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POSTCARD_NOT_OWNED"));
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(1L, 1, null, "안녕")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, style, stranger.getId(), "안녕")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_NOT_FOUND"));
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, style, null, "가".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, style, null, "   ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, 5, null, "안녕")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawalRemovesLettersAndPostcards() throws Exception {
        User me = user("돌쌓기");
        User friend = user("지기");
        befriend(me, friend);
        visit(me, HOMIGOT);
        mockMvc.perform(as(me, post("/api/v1/postcards/letters")).content(letter(HOMIGOT, ownedStyle(me, HOMIGOT), friend.getId(), "안녕")))
                .andExpect(status().isCreated());

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", me.getId());

        List<Integer> left = jdbcTemplate.queryForList(
                "SELECT (SELECT COUNT(*) FROM postcard_letters) + (SELECT COUNT(*) FROM postcards WHERE user_id = ?)",
                Integer.class, me.getId());
        assertThat(left).containsExactly(0);
    }
}
