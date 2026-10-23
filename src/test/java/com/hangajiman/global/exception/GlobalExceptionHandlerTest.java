package com.hangajiman.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @RestController
    static class FailingController {

        @GetMapping("/test/stone")
        void stone() {
            throw new BusinessException(ErrorCode.STONE_ALREADY_TODAY);
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("boom");
        }
    }
}
