package com.hangajiman.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hangajiman.global.exception.BusinessException;

// 실제 카카오·Google 서버 대신 MockRestServiceServer로 응답을 흉내 냅니다.
class SocialClientTest {

    private static final String KAKAO = "https://kapi.kakao.com";
    private static final String TOKENINFO = "https://oauth2.googleapis.com/tokeninfo";
    private static final String CLIENT_ID = "my-app.apps.googleusercontent.com";

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    // --- 카카오 ---

    @Test
    void kakaoProfile() {
        server.expect(requestTo(KAKAO + "/v2/user/me"))
                .andExpect(header("Authorization", "Bearer kakao-token"))
                .andRespond(withSuccess("""
                        {"id": 3412345678,
                         "kakao_account": {"profile": {"profile_image_url": "https://k.kakaocdn.net/p.jpg"}}}
                        """, MediaType.APPLICATION_JSON));

        SocialProfile profile = new KakaoClient(restClient, KAKAO, "").getProfile("kakao-token");

        assertThat(profile.providerUserId()).isEqualTo("3412345678");
        assertThat(profile.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/p.jpg");
    }

    @Test
    void kakaoInvalidToken() {
        server.expect(requestTo(KAKAO + "/v2/user/me")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> new KakaoClient(restClient, KAKAO, "").getProfile("bad"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("소셜 로그인에 실패했어요.");
    }

    // --- 웹 카카오 로그인 (인가 코드) ---

    private static final String KAUTH = "https://kauth.kakao.com";

    @Test
    void kakaoAuthorizeUrlUsesRestKeyAndEncodesRedirect() {
        String url = new KakaoWebLoginClient(restClient, KAUTH, "rest-key", "").authorizeUrl("https://han-gajiman.github.io/login", "abc");

        assertThat(url).startsWith(KAUTH + "/oauth/authorize?")
                .contains("client_id=rest-key", "response_type=code", "state=abc",
                        "redirect_uri=https://han-gajiman.github.io/login");
    }

    @Test
    void kakaoExchangeCodeSendsSecretWhenSet() {
        server.expect(requestTo(KAUTH + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "authorization_code", "client_id", "rest-key", "code", "c1",
                        "redirect_uri", "https://x/login", "client_secret", "sec")))
                .andRespond(withSuccess("{\"access_token\": \"kakao-token\", \"token_type\": \"bearer\"}", MediaType.APPLICATION_JSON));

        String token = new KakaoWebLoginClient(restClient, KAUTH, "rest-key", "sec").exchangeCode("c1", "https://x/login");

        assertThat(token).isEqualTo("kakao-token");
        server.verify();
    }

    @Test
    void kakaoExchangeBadCodeOrNoKeyFails() {
        server.expect(requestTo(KAUTH + "/oauth/token")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> new KakaoWebLoginClient(restClient, KAUTH, "rest-key", "").exchangeCode("bad", "https://x/login"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new KakaoWebLoginClient(restClient, KAUTH, "", "").authorizeUrl("https://x/login", "s"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void kakaoUnlinkWithAdminKey() {
        server.expect(requestTo(KAKAO + "/v1/user/unlink"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK admin-key"))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "target_id_type", "user_id", "target_id", "3412345678")))
                .andRespond(withSuccess("{\"id\": 3412345678}", MediaType.APPLICATION_JSON));

        new KakaoClient(restClient, KAKAO, "admin-key").unlink("3412345678");

        server.verify();
    }

    @Test
    void kakaoUnlinkSkippedWithoutAdminKey() {
        new KakaoClient(restClient, KAKAO, "").unlink("3412345678");

        server.verify(); // 요청을 보내지 않음
    }

    // --- Google ---

    private void googleResponds(String aud) {
        server.expect(requestTo(TOKENINFO + "?id_token=google-id-token"))
                .andRespond(withSuccess("""
                        {"iss": "https://accounts.google.com", "aud": "%s", "sub": "1098765",
                         "picture": "https://lh3.googleusercontent.com/a.jpg", "exp": "9999999999"}
                        """.formatted(aud), MediaType.APPLICATION_JSON));
    }

    @Test
    void googleValidIdToken() {
        googleResponds(CLIENT_ID);

        SocialProfile profile = new GoogleClient(restClient, TOKENINFO, "other-id, " + CLIENT_ID)
                .verifyIdToken("google-id-token");

        assertThat(profile.providerUserId()).isEqualTo("1098765");
        assertThat(profile.profileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/a.jpg");
    }

    @Test
    void googleTokenForAnotherApp() {
        googleResponds("someone-elses-app.apps.googleusercontent.com");

        assertThatThrownBy(() -> new GoogleClient(restClient, TOKENINFO, CLIENT_ID).verifyIdToken("google-id-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void googleInvalidToken() {
        server.expect(requestTo(TOKENINFO + "?id_token=google-id-token")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> new GoogleClient(restClient, TOKENINFO, CLIENT_ID).verifyIdToken("google-id-token"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void googleRejectedWhenClientIdsNotConfigured() {
        assertThatThrownBy(() -> new GoogleClient(restClient, TOKENINFO, "").verifyIdToken("google-id-token"))
                .isInstanceOf(BusinessException.class);
        server.verify(); // Google에 요청하지 않음
    }
}
