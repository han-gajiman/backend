package com.hangajiman.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.auth.mail.PasswordResetMailer;
import com.hangajiman.auth.repository.RefreshTokenRepository;
import com.hangajiman.user.repository.UserRepository;

// 아이디·비밀번호 찾기. 메일 대신 가짜 메일러로 보낸 코드를 가로챕니다. DB는 실제(hangajiman_test), 테스트마다 롤백
// 요청 횟수 제한이 IP 기준이라 테스트마다 다른 IP를 씁니다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountRecoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private PasswordResetMailer mailer;

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private final String ip = "10.0.0." + NEXT_IP.getAndIncrement();

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private String signup(String nickname) throws Exception {
        String email = "r" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        postJson("/api/v1/auth/signup/local",
                "{\"email\": \"" + email + "\", \"password\": \"password123\", \"nickname\": \"" + nickname + "\"}")
                .andExpect(status().isOk());
        return email;
    }

    private String requestCode(String email) throws Exception {
        postJson("/api/v1/auth/password/reset-code", "{\"email\": \"" + email + "\"}").andExpect(status().isNoContent());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(email), code.capture());
        return code.getValue();
    }

    private ResultActions reset(String email, String code, String newPassword) throws Exception {
        return postJson("/api/v1/auth/password/reset",
                "{\"email\": \"" + email + "\", \"code\": \"" + code + "\", \"newPassword\": \"" + newPassword + "\"}");
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login/local", "{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}");
    }

    @Test
    void findEmailReturnsMaskedEmails() throws Exception {
        String nickname = "찾기" + UUID.randomUUID().toString().substring(0, 6);
        String email = signup(nickname);

        postJson("/api/v1/auth/find-email", "{\"nickname\": \"" + nickname + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emails[0]").value(email.substring(0, 2) + "***@ex***.com"));
        postJson("/api/v1/auth/find-email", "{\"nickname\": \"없는닉네임\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emails").isEmpty());
    }

    @Test
    void resetPasswordWithCodeThenLoginWithNewPassword() throws Exception {
        String email = signup("비번찾기");
        String code = requestCode(email);
        assertThat(code).matches("\\d{6}");

        reset(email, code, "newpassword1").andExpect(status().isNoContent());

        login(email, "password123").andExpect(status().isUnauthorized());
        login(email, "newpassword1").andExpect(status().isOk());
        // 비밀번호를 바꾸면 기존 로그인(가입할 때 받은 Refresh Token)은 모두 끊김
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(refreshTokenRepository.findAll()).filteredOn(t -> t.getUserId().equals(userId)).hasSize(1);
        // 한 번 쓴 코드는 다시 못 씀
        reset(email, code, "another123").andExpect(status().isBadRequest());
    }

    @Test
    void wrongCodeFailsAndFiveMistakesInvalidateCode() throws Exception {
        String email = signup("오답");
        String code = requestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            reset(email, wrong, "newpassword1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("AUTH_RESET_CODE_INVALID"));
        }
        // 5번 틀리면 맞는 코드도 무효
        reset(email, code, "newpassword1").andExpect(status().isBadRequest());
    }

    @Test
    void unknownEmailLooksTheSameButSendsNothing() throws Exception {
        postJson("/api/v1/auth/password/reset-code", "{\"email\": \"nobody@example.com\"}").andExpect(status().isNoContent());
        verify(mailer, never()).sendCode(anyString(), anyString());

        reset("nobody@example.com", "123456", "newpassword1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_RESET_CODE_INVALID"));
    }

    @Test
    void resendWithinSixtySecondsIsIgnored() throws Exception {
        String email = signup("재전송");
        requestCode(email);
        postJson("/api/v1/auth/password/reset-code", "{\"email\": \"" + email + "\"}").andExpect(status().isNoContent());
        verify(mailer, times(1)).sendCode(eq(email), anyString());
    }

    @Test
    void tooManyFindEmailRequestsAreLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            postJson("/api/v1/auth/find-email", "{\"nickname\": \"제한\"}").andExpect(status().isOk());
        }
        postJson("/api/v1/auth/find-email", "{\"nickname\": \"제한\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void invalidResetInput() throws Exception {
        reset("me@example.com", "12ab", "newpassword1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        reset("me@example.com", "123456", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
