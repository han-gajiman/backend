package com.hangajiman.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void businessExceptionUsesErrorCodeStatusAndEnvelope() throws Exception {
        mockMvc.perform(get("/test/stone"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("STONE_ALREADY_TODAY"))
                .andExpect(jsonPath("$.error.message").value("오늘은 이미 돌을 쌓았어요."));
    }

    @Test
    void unexpectedExceptionIsHiddenAsInternalError() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void wrongContentTypeIsInvalidInputNot500() throws Exception {
        mockMvc.perform(post("/test/json").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void missingOrWrongTypeParameterIsInvalidInput() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/test/param").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void unsupportedMethodIsInvalidInput() throws Exception {
        mockMvc.perform(delete("/test/stone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/stone")
        void stone() {
            throw new BusinessException(ErrorCode.STONE_ALREADY_TODAY);
        }

        @PostMapping("/test/json")
        void json(@RequestBody Map<String, Object> body) {
        }

        @GetMapping("/test/param")
        void param(@RequestParam int n) {
        }

        @GetMapping("/test/missing")
        void missing() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "test/missing");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
