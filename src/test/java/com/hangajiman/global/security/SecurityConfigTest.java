package com.hangajiman.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// DevTokenController가 등록되도록 local 프로필로 실행
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void publicPathWithoutToken() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedPathWithoutTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/dev/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void protectedPathWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/dev/me").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void protectedPathWithValidTokenExposesUserId() throws Exception {
        String token = jwtProvider.createAccessToken(7L);

        mockMvc.perform(get("/api/v1/dev/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7));
    }

    @Test
    void devTokenEndpointIssuesUsableToken() throws Exception {
        mockMvc.perform(get("/api/v1/dev/token").param("userId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}
