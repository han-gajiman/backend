package com.hangajiman.spot.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

// 실제 DB(hangajiman_test)의 시드 명소 10곳으로 확인합니다.
@SpringBootTest
@AutoConfigureMockMvc
class SpotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllSpots() throws Exception {
        mockMvc.perform(get("/api/v1/spots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(10)))
                .andExpect(jsonPath("$.data[0].name").value("팔공산 갓바위"))
                .andExpect(jsonPath("$.data[1].category").value("PASS"))
                .andExpect(jsonPath("$.data[1].latitude").value(36.76844))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void filterByCategory() throws Exception {
        mockMvc.perform(get("/api/v1/spots").param("category", "PASS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("문경새재 과거길"));
    }

    @Test
    void unknownCategoryReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/spots").param("category", "NONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
