package com.hangajiman.notification.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.notification.domain.Notification;
import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.service.NotificationService;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

// NOTI-01 알림함. 실제 DB(hangajiman_test), 테스트마다 롤백 (커밋되지 않으므로 푸시도 나가지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private Clock clock;

    private User onboardedUser(String nickname) {
        User user = User.create(SocialProvider.KAKAO, "kakao-" + System.nanoTime(), null);
        user.completeOnboarding(nickname, clock.instant());
        return userRepository.saveAndFlush(user);
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(user.getId()));
    }

    @Test
    void myNotificationsLatestFirst() throws Exception {
        User me = onboardedUser("소원지기");
        User friend = onboardedUser("지기");
        User other = onboardedUser("남");
        notificationService.notify(me.getId(), null, NotificationType.TOWER_COMPLETED, 10L, "30일 돌탑이 완성됐어요");
        notificationService.notify(me.getId(), friend.getId(), NotificationType.CHEER, 7L, "지기님이 응원을 보냈어요.");
        notificationService.notify(other.getId(), friend.getId(), NotificationType.NUDGE, 3L, "남의 알림");

        mockMvc.perform(as(me, get("/api/v1/notifications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[*].type", contains("CHEER", "TOWER_COMPLETED")))
                .andExpect(jsonPath("$.data.content[0].targetId").value(7))
                .andExpect(jsonPath("$.data.content[0].message").value("지기님이 응원을 보냈어요."))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false))
                .andExpect(jsonPath("$.data.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void markRead() throws Exception {
        User me = onboardedUser("소원지기");
        Notification n = notificationService.notify(me.getId(), null, NotificationType.TOWER_COMPLETED, 10L, "완성");

        mockMvc.perform(as(me, patch("/api/v1/notifications/" + n.getId() + "/read")))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(me, get("/api/v1/notifications")))
                .andExpect(jsonPath("$.data.content[0].isRead").value(true));

        // 이미 읽은 알림을 다시 읽어도 성공
        mockMvc.perform(as(me, patch("/api/v1/notifications/" + n.getId() + "/read")))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotReadOthersOrMissingNotification() throws Exception {
        User me = onboardedUser("소원지기");
        User other = onboardedUser("남");
        Notification others = notificationService.notify(other.getId(), null, NotificationType.TOWER_COMPLETED, 1L, "남");

        mockMvc.perform(as(me, patch("/api/v1/notifications/" + others.getId() + "/read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(as(me, patch("/api/v1/notifications/999999999/read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void paging() throws Exception {
        User me = onboardedUser("소원지기");
        for (int i = 0; i < 3; i++) {
            notificationService.notify(me.getId(), null, NotificationType.TOWER_COMPLETED, (long) i, "알림 " + i);
        }

        mockMvc.perform(as(me, get("/api/v1/notifications").param("size", "2")))
                .andExpect(jsonPath("$.data.content[*].message", contains("알림 2", "알림 1")))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(as(me, get("/api/v1/notifications").param("page", "1").param("size", "2")))
                .andExpect(jsonPath("$.data.content[*].message", contains("알림 0")))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }
}
