package com.hangajiman.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hangajiman.auth.social.GoogleClient;
import com.hangajiman.auth.social.KakaoClient;
import com.hangajiman.auth.social.KakaoWebLoginClient;
import com.hangajiman.auth.social.SocialProfile;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.repository.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// 카카오·Google 서버 대신 가짜 클라이언트를 씁니다. DB는 실제(hangajiman_test), 테스트마다 롤백
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @MockBean
    private KakaoClient kakaoClient;

    @MockBean
    private GoogleClient googleClient;

    @MockBean
    private KakaoWebLoginClient kakaoWebLoginClient;

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode dataOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode kakaoLogin(String kakaoUserId) throws Exception {
        given(kakaoClient.getProfile("kakao-token")).willReturn(new SocialProfile(kakaoUserId));
        return dataOf(postJson("/api/v1/auth/kakao", "{\"accessToken\": \"kakao-token\"}").andExpect(status().isOk()));
    }

    private String refreshBody(String refreshToken) {
        return "{\"refreshToken\": \"" + refreshToken + "\"}";
    }

    @Test
    void kakaoFirstLoginSignsUpThenSecondLoginFindsSameUser() throws Exception {
        JsonNode first = kakaoLogin("12345");
        assertThat(first.get("isNewUser").asBoolean()).isTrue();
        assertThat(first.get("onboarded").asBoolean()).isFalse();
        assertThat(first.get("accessToken").asText()).isNotBlank();
        assertThat(first.get("refreshToken").asText()).isNotBlank();

        JsonNode second = kakaoLogin("12345");
        assertThat(second.get("isNewUser").asBoolean()).isFalse();

        var user = userRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "12345").orElseThrow();
        assertThat(user.getProfileImageUrl()).isNull(); // 프로필 사진 URL은 저장하지 않음 (11/10)
        // 원문은 저장하지 않고 해시만
        Integer rawSaved = jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class, first.get("refreshToken").asText());
        assertThat(rawSaved).isZero();
    }

    @Test
    void googleLogin() throws Exception {
        given(googleClient.verifyIdToken("google-id-token")).willReturn(new SocialProfile("google-sub-1"));

        JsonNode data = dataOf(postJson("/api/v1/auth/google", "{\"idToken\": \"google-id-token\"}")
                .andExpect(status().isOk()));

        assertThat(data.get("isNewUser").asBoolean()).isTrue();
        assertThat(userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub-1")).isPresent();
    }

    @Test
    void webKakaoCodeLoginUsesSameKakaoAccount() throws Exception {
        JsonNode app = kakaoLogin("777");
        given(kakaoWebLoginClient.exchangeCode("auth-code", "https://han-gajiman.github.io/login")).willReturn("kakao-token");

        JsonNode web = dataOf(postJson("/api/v1/auth/kakao/web/code",
                "{\"code\": \"auth-code\", \"redirectUri\": \"https://han-gajiman.github.io/login\"}")
                .andExpect(status().isOk()));

        assertThat(app.get("isNewUser").asBoolean()).isTrue();
        assertThat(web.get("isNewUser").asBoolean()).isFalse(); // 앱에서 가입한 같은 카카오 회원
        assertThat(web.get("accessToken").asText()).isNotBlank();
    }

    @Test
    void webKakaoBadCodeReturns401() throws Exception {
        given(kakaoWebLoginClient.exchangeCode("bad", "https://x/login"))
                .willThrow(new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED));

        postJson("/api/v1/auth/kakao/web/code", "{\"code\": \"bad\", \"redirectUri\": \"https://x/login\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SOCIAL_FAILED"));
    }

    @Test
    void webKakaoAuthorizeUrlIsPublic() throws Exception {
        given(kakaoWebLoginClient.authorizeUrl("https://x/login", "s1")).willReturn("https://kauth.kakao.com/oauth/authorize?x");

        mockMvc.perform(get("/api/v1/auth/kakao/web/authorize-url").param("redirectUri", "https://x/login").param("state", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://kauth.kakao.com/oauth/authorize?x"));
    }

    @Test
    void invalidSocialTokenReturns401() throws Exception {
        given(kakaoClient.getProfile("bad")).willThrow(new BusinessException(ErrorCode.AUTH_SOCIAL_FAILED));

        postJson("/api/v1/auth/kakao", "{\"accessToken\": \"bad\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_SOCIAL_FAILED"));
    }

    @Test
    void emptyTokenIsInvalidInput() throws Exception {
        postJson("/api/v1/auth/kakao", "{\"accessToken\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void refreshRotatesToken() throws Exception {
        String oldRefresh = kakaoLogin("rotate").get("refreshToken").asText();

        JsonNode renewed = dataOf(postJson("/api/v1/auth/refresh", refreshBody(oldRefresh)).andExpect(status().isOk()));
        assertThat(renewed.get("refreshToken").asText()).isNotEqualTo(oldRefresh);

        // 이미 교체된 토큰은 다시 쓸 수 없음
        postJson("/api/v1/auth/refresh", refreshBody(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_REFRESH_TOKEN"));
        postJson("/api/v1/auth/refresh", refreshBody(renewed.get("refreshToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        JsonNode login = kakaoLogin("expired");
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 day'");
        entityManager.clear(); // 같은 트랜잭션에서 읽어 둔 토큰 객체를 비워 DB 값을 다시 읽게 함

        postJson("/api/v1/auth/refresh", refreshBody(login.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode login = kakaoLogin("logout");
        String refreshToken = login.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        postJson("/api/v1/auth/refresh", refreshBody(refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresAccessToken() throws Exception {
        postJson("/api/v1/auth/logout", refreshBody("anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void withdrawDeletesUserAndUnlinksKakao() throws Exception {
        JsonNode login = kakaoLogin("withdraw");
        String bearer = "Bearer " + login.get("accessToken").asText();

        mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        verify(kakaoClient).unlink("withdraw");
        assertThat(userRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "withdraw")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
        // 탈퇴 후 같은 토큰으로는 아무것도 못 함
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", refreshBody(login.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleWithdrawDoesNotCallKakao() throws Exception {
        given(googleClient.verifyIdToken("g")).willReturn(new SocialProfile("google-withdraw"));
        JsonNode login = dataOf(postJson("/api/v1/auth/google", "{\"idToken\": \"g\"}"));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        verify(kakaoClient, never()).unlink(ArgumentMatchers.anyString());
        assertThat(userRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-withdraw")).isEmpty();
    }

    // --- 자체 로그인 (이메일·비밀번호) ---

    private String signupBody(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\", \"nickname\": \"돌쌓기\"}";
    }

    private String loginBody(String email, String password) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}";
    }

    @Test
    void localSignupThenLogin() throws Exception {
        JsonNode signup = dataOf(postJson("/api/v1/auth/signup/local", signupBody("Stone@Example.com", "password123"))
                .andExpect(status().isOk()));
        assertThat(signup.get("isNewUser").asBoolean()).isTrue();
        assertThat(signup.get("onboarded").asBoolean()).isFalse(); // 약관 동의는 온보딩에서

        // 이메일은 소문자로 저장되고, 비밀번호는 해시로만 저장
        String saved = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE email = 'stone@example.com' AND provider = 'LOCAL'", String.class);
        assertThat(saved).isNotEqualTo("password123").startsWith("$2");

        JsonNode login = dataOf(postJson("/api/v1/auth/login/local", loginBody("stone@example.com", "password123"))
                .andExpect(status().isOk()));
        assertThat(login.get("isNewUser").asBoolean()).isFalse();
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("돌쌓기"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        postJson("/api/v1/auth/signup/local", signupBody("dup@example.com", "password123")).andExpect(status().isOk());

        postJson("/api/v1/auth/signup/local", signupBody("DUP@example.com", "password456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_DUPLICATED"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetSameError() throws Exception {
        postJson("/api/v1/auth/signup/local", signupBody("me@example.com", "password123")).andExpect(status().isOk());

        postJson("/api/v1/auth/login/local", loginBody("me@example.com", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_LOGIN_FAILED"));
        postJson("/api/v1/auth/login/local", loginBody("nobody@example.com", "password123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void invalidSignupInput() throws Exception {
        postJson("/api/v1/auth/signup/local", signupBody("not-an-email", "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        postJson("/api/v1/auth/signup/local", signupBody("short@example.com", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void localWithdrawDoesNotCallKakao() throws Exception {
        JsonNode signup = dataOf(postJson("/api/v1/auth/signup/local", signupBody("bye@example.com", "password123")));

        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signup.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        verify(kakaoClient, never()).unlink(ArgumentMatchers.anyString());
        assertThat(userRepository.findByEmail("bye@example.com")).isEmpty();
    }
}
