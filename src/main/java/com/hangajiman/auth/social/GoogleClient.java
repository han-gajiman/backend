package com.hangajiman.auth.social;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;

/**
 * Google ID 토큰 검증 (tokeninfo 엔드포인트).
 * 서명·만료는 Google이 확인하고, 우리 앱용 토큰인지(aud)와 발급자(iss)는 여기서 확인합니다.
 * GOOGLE_CLIENT_IDS: 앱의 OAuth 클라이언트 ID들 (Android·iOS·웹이 다르면 쉼표로 구분)
 */
@Component
public class GoogleClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleClient.class);
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final RestClient restClient;
    private final String tokenInfoUrl;
    private final List<String> clientIds;

    public GoogleClient(RestClient socialRestClient,
                        @Value("${google.tokeninfo-url}") String tokenInfoUrl,
                        @Value("${google.client-ids:}") String clientIds) {
        this.restClient = socialRestClient;
        this.tokenInfoUrl = tokenInfoUrl;
        this.clientIds = Arrays.stream(clientIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }

    // 무효·만료·다른 앱의 토큰이면 401 AUTH_SOCIAL_FAILED
    public SocialProfile verifyIdToken(String idToken) {
        if (clientIds.isEmpty()) {
            log.error("GOOGLE_CLIENT_IDS가 설정되지 않아 Google 로그인을 처리할 수 없습니다.");
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }

        JsonNode body;
        try {
            body = restClient.get()
                    .uri(tokenInfoUrl + "?id_token={idToken}", idToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.info("Google 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }

        if (body == null
                || !clientIds.contains(body.path("aud").asText())
                || !ISSUERS.contains(body.path("iss").asText())
                || body.path("sub").asText().isEmpty()) {
            log.info("Google 토큰의 aud/iss/sub가 맞지 않습니다.");
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }
        return new SocialProfile(body.get("sub").asText(), body.path("picture").asText(null));
    }
}
