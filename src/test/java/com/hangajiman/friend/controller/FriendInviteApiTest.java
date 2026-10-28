package com.hangajiman.friend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.friend.domain.FriendInvite;
import com.hangajiman.friend.repository.FriendInviteRepository;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.jayway.jsonpath.JsonPath;

// FRIEND-01 초대 링크 생성·수락
class FriendInviteApiTest extends FriendApiTestSupport {

    @Autowired
    private FriendInviteRepository inviteRepository;

    private String createInvite(User inviter) throws Exception {
        String body = mockMvc.perform(as(inviter, post("/api/v1/friends/invites")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.inviteCode");
    }

    @Test
    void createInviteLink() throws Exception {
        User inviter = user("석현");

        String body = mockMvc.perform(as(inviter, post("/api/v1/friends/invites")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inviteCode").value(matchesPattern("[A-Za-z0-9]{8}")))
                .andExpect(jsonPath("$.data.inviteUrl").value(matchesPattern("https://test\\.hangajiman\\.app/invite/[A-Za-z0-9]{8}")))
                .andExpect(jsonPath("$.data.expiresAt").value(matchesPattern(".*\\+09:00")))
                .andReturn().getResponse().getContentAsString();

        // 만료 = 지금 + 7일
        OffsetDateTime expiresAt = OffsetDateTime.parse(JsonPath.read(body, "$.data.expiresAt"));
        Duration ttl = Duration.between(clock.instant(), expiresAt.toInstant());
        assertThat(ttl).isBetween(Duration.ofDays(7).minusMinutes(1), Duration.ofDays(7));
    }

    @Test
    void acceptInviteMakesBothFriends() throws Exception {
        User inviter = user("석현");
        User invitee = user("지기");
        String code = createInvite(inviter);

        mockMvc.perform(as(invitee, post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendId").value(inviter.getId()))
                .andExpect(jsonPath("$.data.nickname").value("석현"));

        assertThat(friendshipRepository.existsByUserIdAndFriendId(inviter.getId(), invitee.getId())).isTrue();
        assertThat(friendshipRepository.existsByUserIdAndFriendId(invitee.getId(), inviter.getId())).isTrue();
    }

    @Test
    void sameLinkCanBeAcceptedByManyPeople() throws Exception {
        User inviter = user("석현");
        String code = createInvite(inviter);

        mockMvc.perform(as(user("친구1"), post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isOk());
        mockMvc.perform(as(user("친구2"), post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isOk());
    }

    @Test
    void acceptTwiceFails() throws Exception {
        User inviter = user("석현");
        User invitee = user("지기");
        String code = createInvite(inviter);
        mockMvc.perform(as(invitee, post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isOk());

        mockMvc.perform(as(invitee, post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIEND_ALREADY"));
    }

    @Test
    void acceptOwnInviteFails() throws Exception {
        User inviter = user("석현");
        String code = createInvite(inviter);

        mockMvc.perform(as(inviter, post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("FRIEND_SELF_INVITE"));
    }

    @Test
    void unknownCodeFails() throws Exception {
        mockMvc.perform(as(user("지기"), post("/api/v1/friends/invites/{code}/accept", "nope1234")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_INVITE_NOT_FOUND"));
    }

    @Test
    void expiredCodeFails() throws Exception {
        User inviter = user("석현");
        inviteRepository.saveAndFlush(new FriendInvite(inviter.getId(), "expired1", clock.instant().minusSeconds(1)));

        mockMvc.perform(as(user("지기"), post("/api/v1/friends/invites/{code}/accept", "expired1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FRIEND_INVITE_NOT_FOUND"));
    }

    @Test
    void notOnboardedUserCannotAccept() throws Exception {
        String code = createInvite(user("석현"));
        User notOnboarded = userRepository.saveAndFlush(User.create(SocialProvider.GOOGLE, "google-" + System.nanoTime(), null));

        mockMvc.perform(as(notOnboarded, post("/api/v1/friends/invites/{code}/accept", code)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void withoutTokenFails() throws Exception {
        mockMvc.perform(post("/api/v1/friends/invites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }
}
