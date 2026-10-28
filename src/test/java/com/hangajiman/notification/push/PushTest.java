package com.hangajiman.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

// Expo 푸시 서버 대신 MockRestServiceServer, DB 대신 가짜 리포지토리
class PushTest {

    private static final String URL = "https://exp.host/--/api/v2/push/send";
    private static final String TOKEN = "ExponentPushToken[abc123]";

    private MockRestServiceServer server;
    private ExpoPushClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ExpoPushClient(builder.build(), URL, "");
    }

    // --- ExpoPushClient ---

    @Test
    void sendsExpoMessage() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"to": "ExponentPushToken[abc123]", "title": "한 가지만", "body": "지기님이 응원을 보냈어요.",
                         "sound": "default", "data": {"type": "CHEER", "targetId": 7}}
                        """))
                .andRespond(withSuccess("{\"data\": {\"status\": \"ok\", \"id\": \"r1\"}}", MediaType.APPLICATION_JSON));

        ExpoPushClient.Result result = client.send(TOKEN, "지기님이 응원을 보냈어요.", Map.of("type", "CHEER", "targetId", 7));

        assertThat(result).isEqualTo(ExpoPushClient.Result.SENT);
        server.verify();
    }

    @Test
    void deviceNotRegistered() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"data": {"status": "error", "message": "not registered", "details": {"error": "DeviceNotRegistered"}}}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.send(TOKEN, "msg", Map.of())).isEqualTo(ExpoPushClient.Result.DEVICE_NOT_REGISTERED);
    }

    @Test
    void serverErrorDoesNotThrow() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(client.send(TOKEN, "msg", Map.of())).isEqualTo(ExpoPushClient.Result.FAILED);
    }

    @Test
    void recognizesExpoTokens() {
        assertThat(ExpoPushClient.isExpoToken("ExponentPushToken[x]")).isTrue();
        assertThat(ExpoPushClient.isExpoToken("ExpoPushToken[x]")).isTrue();
        assertThat(ExpoPushClient.isExpoToken("fcm-raw-token")).isFalse();
        assertThat(ExpoPushClient.isExpoToken(null)).isFalse();
    }

    // --- NotificationPushListener ---

    private static User userWithToken(String token) {
        User user = User.create(SocialProvider.KAKAO, "k", null);
        user.updatePushToken(token);
        return user;
    }

    private final NotificationCreatedEvent event = new NotificationCreatedEvent(1L, NotificationType.NUDGE, 3L, "콕!");

    @Test
    void listenerSendsToUsersToken() {
        UserRepository users = mock(UserRepository.class);
        ExpoPushClient expo = mock(ExpoPushClient.class);
        given(users.findById(1L)).willReturn(Optional.of(userWithToken(TOKEN)));
        given(expo.send(eq(TOKEN), eq("콕!"), any())).willReturn(ExpoPushClient.Result.SENT);

        new NotificationPushListener(users, expo).onNotificationCreated(event);

        verify(expo).send(TOKEN, "콕!", Map.of("type", "NUDGE", "targetId", 3L));
    }

    @Test
    void listenerSkipsUserWithoutToken() {
        UserRepository users = mock(UserRepository.class);
        ExpoPushClient expo = mock(ExpoPushClient.class);
        given(users.findById(1L)).willReturn(Optional.of(userWithToken(null)));

        new NotificationPushListener(users, expo).onNotificationCreated(event);

        verify(expo, never()).send(anyString(), anyString(), any());
    }

    @Test
    void listenerClearsTokenOfUnregisteredDevice() {
        UserRepository users = mock(UserRepository.class);
        ExpoPushClient expo = mock(ExpoPushClient.class);
        User user = userWithToken(TOKEN);
        given(users.findById(1L)).willReturn(Optional.of(user));
        given(expo.send(anyString(), anyString(), any())).willReturn(ExpoPushClient.Result.DEVICE_NOT_REGISTERED);

        new NotificationPushListener(users, expo).onNotificationCreated(event);

        assertThat(user.getPushToken()).isNull();
    }
}
