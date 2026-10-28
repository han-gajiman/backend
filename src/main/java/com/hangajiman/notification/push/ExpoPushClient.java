package com.hangajiman.notification.push;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Expo 푸시 서버로 발송 (https://docs.expo.dev/push-notifications/sending-notifications/)
 * 실패해도 예외를 던지지 않습니다. 알림함 저장은 이미 끝났으니 푸시는 최선을 다해 보내기만 합니다.
 * EXPO_ACCESS_TOKEN: Expo의 강화된 푸시 보안을 켰을 때만 필요 (기본은 인증 없음)
 */
@Component
public class ExpoPushClient {

    public enum Result { SENT, DEVICE_NOT_REGISTERED, FAILED }

    private static final Logger log = LoggerFactory.getLogger(ExpoPushClient.class);
    private static final String TITLE = "한 가지만";

    private final RestClient restClient;
    private final String pushUrl;
    private final String accessToken;

    public ExpoPushClient(RestClient socialRestClient,
                          @Value("${expo.push-url}") String pushUrl,
                          @Value("${expo.access-token:}") String accessToken) {
        this.restClient = socialRestClient;
        this.pushUrl = pushUrl;
        this.accessToken = accessToken;
    }

    public static boolean isExpoToken(String token) {
        return token != null && (token.startsWith("ExponentPushToken[") || token.startsWith("ExpoPushToken["));
    }

    public Result send(String pushToken, String body, Map<String, Object> data) {
        try {
            JsonNode response = restClient.post()
                    .uri(pushUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (accessToken != null && !accessToken.isBlank()) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
                        }
                    })
                    .body(Map.of("to", pushToken, "title", TITLE, "body", body, "sound", "default", "data", data))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode ticket = response == null ? null : response.path("data");
            if (ticket != null && "ok".equals(ticket.path("status").asText())) {
                return Result.SENT;
            }
            String error = ticket == null ? "" : ticket.path("details").path("error").asText();
            if ("DeviceNotRegistered".equals(error)) {
                return Result.DEVICE_NOT_REGISTERED;
            }
            log.warn("Expo 푸시 실패: {}", response);
            return Result.FAILED;
        } catch (RestClientException e) {
            log.warn("Expo 푸시 요청 실패: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}
