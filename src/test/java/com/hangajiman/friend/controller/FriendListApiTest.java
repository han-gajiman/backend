package com.hangajiman.friend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;

// FRIEND-02 친구 목록 + 돌탑 요약
class FriendListApiTest extends FriendApiTestSupport {

    @Test
    void friendWithPublicWishShowsSummary() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        Wish wish = activeWish(friend, Visibility.FRIENDS);
        stone(wish, today().minusDays(1));
        stone(wish, today());

        mockMvc.perform(as(me, get("/api/v1/friends")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].friendId").value(friend.getId()))
                .andExpect(jsonPath("$.data.content[0].nickname").value("지기"))
                .andExpect(jsonPath("$.data.content[0].wish.wishId").value(wish.getId()))
                .andExpect(jsonPath("$.data.content[0].wish.content").value("지기의 소원"))
                .andExpect(jsonPath("$.data.content[0].wish.stoneCount").value(2))
                .andExpect(jsonPath("$.data.content[0].wish.stonedToday").value(true))
                .andExpect(jsonPath("$.data.content[0].wish.spotId").value(wish.getSpotId()))
                .andExpect(jsonPath("$.data.content[0].wish.spotVisited").value(false))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void privateWishOrNoWishIsHidden() throws Exception {
        User me = user("석현");
        User privateFriend = user("비공개");
        User noWishFriend = user("돌탑없음");
        befriend(me, privateFriend);
        befriend(me, noWishFriend);
        activeWish(privateFriend, Visibility.PRIVATE);

        mockMvc.perform(as(me, get("/api/v1/friends")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].wish").isEmpty())
                .andExpect(jsonPath("$.data.content[1].wish").isEmpty());
    }

    @Test
    void notStonedTodayAndZeroStones() throws Exception {
        User me = user("석현");
        User friend = user("지기");
        befriend(me, friend);
        activeWish(friend, Visibility.FRIENDS);

        mockMvc.perform(as(me, get("/api/v1/friends")))
                .andExpect(jsonPath("$.data.content[0].wish.stoneCount").value(0))
                .andExpect(jsonPath("$.data.content[0].wish.stonedToday").value(false));
    }

    @Test
    void newestFriendFirstAndPaging() throws Exception {
        User me = user("석현");
        User first = user("첫친구");
        User second = user("둘째친구");
        befriend(me, first);
        befriend(me, second);

        mockMvc.perform(as(me, get("/api/v1/friends").param("page", "0").param("size", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].nickname").value("둘째친구"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mockMvc.perform(as(me, get("/api/v1/friends").param("page", "1").param("size", "1")))
                .andExpect(jsonPath("$.data.content[0].nickname").value("첫친구"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void onlyMyFriendsAreListed() throws Exception {
        User me = user("석현");
        User other = user("남");
        befriend(other, user("남의친구"));

        mockMvc.perform(as(me, get("/api/v1/friends")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void pageParamsAreClamped() throws Exception {
        User me = user("석현");

        // PageResponse.pageable 공통 규칙: size는 1~50, 음수 page는 0
        mockMvc.perform(as(me, get("/api/v1/friends").param("size", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(50));
        mockMvc.perform(as(me, get("/api/v1/friends").param("page", "-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0));
    }
}
