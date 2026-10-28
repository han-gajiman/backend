package com.hangajiman.auth.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;

/**
 * 카카오 API (https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
 * - 로그인: 앱이 카카오 SDK로 받은 액세스 토큰으로 회원 정보를 조회해 검증합니다.
 * - 탈퇴: 어드민 키로 연결 끊기 (KAKAO_ADMIN_KEY 환경변수)
 */
@Component
public class KakaoClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoClient.class);

    private final RestClient restClient;
    private final String apiBaseUrl;
    private final String adminKey;

    public KakaoClient(RestClient socialRestClient,
                       @Value("${kakao.api-base-url}") String apiBaseUrl,
                       @Value("${kakao.admin-key:}") String adminKey) {
        this.restClient = socialRestClient;
        this.apiBaseUrl = apiBaseUrl;
        this.adminKey = adminKey;
    }

    // GET /v2/user/me — 토큰이 무효·만료면 401 AUTH_SOCIAL_FAILED
    public SocialProfile getProfile(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(apiBaseUrl + "/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.info("카카오 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }

        if (body == null || !body.hasNonNull("id")) {
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }
        String profileImageUrl = body.path("kakao_account").path("profile").path("profile_image_url").asText(null);
        return new SocialProfile(body.get("id").asText(), profileImageUrl);
    }

    // POST /v1/user/unlink — 탈퇴 시 연결 끊기. 실패해도 탈퇴는 진행합니다(로그만 남김).
    public void unlink(String providerUserId) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("KAKAO_ADMIN_KEY가 없어 카카오 연결 끊기를 건너뜁니다. userId={}", providerUserId);
            return;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);
        try {
            restClient.post()
                    .uri(apiBaseUrl + "/v1/user/unlink")
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("카카오 연결 끊기 실패 (탈퇴는 계속 진행): userId={}, {}", providerUserId, e.getMessage());
        }
    }
}
