package com.hangajiman.auth.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;

/**
 * 웹 카카오 로그인 (REST API 방식, https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api)
 * 앱은 카카오 SDK로 액세스 토큰을 바로 받지만, 웹은 카카오 로그인 화면에서 인가 코드를 받아 돌아옵니다.
 * 인가 코드를 액세스 토큰으로 바꾸는 일은 REST API 키(KAKAO_REST_API_KEY)가 있는 서버가 합니다.
 * 카카오 콘솔에서 Client Secret을 켰다면 KAKAO_CLIENT_SECRET도 넣어야 합니다.
 */
@Component
public class KakaoWebLoginClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoWebLoginClient.class);

    private final RestClient restClient;
    private final String authBaseUrl;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoWebLoginClient(RestClient socialRestClient,
                               @Value("${kakao.auth-base-url:https://kauth.kakao.com}") String authBaseUrl,
                               @Value("${kakao.rest-api-key:}") String restApiKey,
                               @Value("${kakao.client-secret:}") String clientSecret) {
        this.restClient = socialRestClient;
        this.authBaseUrl = authBaseUrl;
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
    }

    // 카카오 로그인 화면 주소. 로그인하면 redirectUri?code=...&state=... 로 돌아옵니다.
    // redirectUri는 카카오 콘솔에 등록된 주소여야 합니다 (카카오가 검사).
    public String authorizeUrl(String redirectUri, String state) {
        requireKey();
        return UriComponentsBuilder.fromHttpUrl(authBaseUrl + "/oauth/authorize")
                .queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    // POST /oauth/token — 인가 코드 → 액세스 토큰. 코드가 틀리거나 이미 썼으면 401 AUTH_SOCIAL_FAILED
    public String exchangeCode(String code, String redirectUri) {
        requireKey();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        JsonNode body;
        try {
            body = restClient.post()
                    .uri(authBaseUrl + "/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.info("카카오 인가 코드 교환 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }
        if (body == null || !body.hasNonNull("access_token")) {
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }
        return body.get("access_token").asText();
    }

    private void requireKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            log.warn("KAKAO_REST_API_KEY가 없어 웹 카카오 로그인을 할 수 없습니다.");
            throw new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED);
        }
    }
}
