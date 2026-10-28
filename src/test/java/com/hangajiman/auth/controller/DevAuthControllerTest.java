package com.hangajiman.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// local 프로필에서만 등록되는 개발용 로그인
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class DevAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode devLogin(String body) throws Exception {
        String json = mockMvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data");
    }

    @Test
    void devLoginIssuesRealTokensAndRefreshWorks() throws Exception {
        JsonNode first = devLogin("{\"devKey\": \"me\"}");
        assertThat(first.get("isNewUser").asBoolean()).isTrue();
        assertThat(first.get("onboarded").asBoolean()).isFalse();

        JsonNode second = devLogin("{\"devKey\": \"me\"}");
        assertThat(second.get("isNewUser").asBoolean()).isFalse();

        // 실제 로그인과 같은 Refresh Token이라 재발급 API가 동작
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + second.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void localProfileAllowsLocalhostCors() throws Exception {
        mockMvc.perform(options("/api/v1/dev/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8765")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8765"));

        mockMvc.perform(options("/api/v1/dev/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDevKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/dev/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devKey\": \"공백 있음\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
