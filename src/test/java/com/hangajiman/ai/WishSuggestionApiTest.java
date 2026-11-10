package com.hangajiman.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.hangajiman.ai.client.WishSuggester;
import com.hangajiman.ai.client.WishSuggester.Suggestion;
import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.user.domain.User;

// WISH-02 AI 추천. 실제 Claude는 부르지 않고 가짜 응답으로 확인합니다.
class WishSuggestionApiTest extends FriendApiTestSupport {

    private static final String BODY = "{\"content\": \"영어로 자연스럽게 대화하기\"}";

    @MockBean
    private WishSuggester suggester;

    private void answer(Suggestion suggestion) {
        when(suggester.suggest(anyString(), any())).thenReturn(Optional.ofNullable(suggestion));
    }

    @Test
    void returnsThreeGoalsAndSpot() throws Exception {
        answer(new Suggestion(List.of("매일 영어 문장 3개 소리 내어 읽기", "하루 10분 영어 영상 따라 말하기", "매일 영어로 일기 한 문장 쓰기"),
                3L, "새로운 나를 시작하는 소원이니 해 뜨는 곳에서 다짐해 봐요.", false));

        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionGoals.length()").value(3))
                .andExpect(jsonPath("$.data.actionGoals[0]").value("매일 영어 문장 3개 소리 내어 읽기"))
                .andExpect(jsonPath("$.data.spot.spotId").value(3))
                .andExpect(jsonPath("$.data.spot.spotName").value("호미곶 해맞이광장"))
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.supportMessage").isEmpty())
                .andExpect(jsonPath("$.data.remaining").value(9));
    }

    @Test
    void unknownSpotIsDroppedButGoalsKept() throws Exception {
        answer(new Suggestion(List.of("가", "나", "다"), 999L, "없는 명소", false));

        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(jsonPath("$.data.actionGoals.length()").value(3))
                .andExpect(jsonPath("$.data.spot").isEmpty())
                .andExpect(jsonPath("$.data.fallback").value(false));
    }

    @Test
    void failureOrBadAnswerUsesDefaultGoals() throws Exception {
        answer(null); // 시간 초과·키 없음·네트워크 오류
        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.actionGoals.length()").value(3))
                .andExpect(jsonPath("$.data.spot").isEmpty());

        answer(new Suggestion(List.of("하나만", "가".repeat(41)), 3L, "이유", false)); // 개수·길이가 맞지 않음
        mockMvc.perform(as(user("소원지기2"), post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(jsonPath("$.data.fallback").value(true));
    }

    @Test
    void crisisWishShowsSupportWithoutCallingAi() throws Exception {
        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content("{\"content\": \"그냥 죽고 싶어\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionGoals.length()").value(0))
                .andExpect(jsonPath("$.data.spot").isEmpty())
                .andExpect(jsonPath("$.data.supportMessage").isString());
        verify(suggester, never()).suggest(anyString(), any());

        answer(new Suggestion(List.of(), 0L, "", true)); // AI가 위험하다고 판단
        mockMvc.perform(as(user("소원지기2"), post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(jsonPath("$.data.supportMessage").isString())
                .andExpect(jsonPath("$.data.actionGoals.length()").value(0));
    }

    @Test
    void tenPerDay() throws Exception {
        answer(null);
        User user = user("소원지기");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(as(user, post("/api/v1/wishes/suggestions")).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.remaining").value(9 - i)); // 기본 문구로 대체돼도 1번으로 셈
        }
        mockMvc.perform(as(user, post("/api/v1/wishes/suggestions")).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMIT"));
        // 다른 회원은 영향 없음
        mockMvc.perform(as(user("다른사람"), post("/api/v1/wishes/suggestions")).content(BODY)).andExpect(status().isOk());
    }

    @Test
    void validation() throws Exception {
        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content("{\"content\": \"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(user("소원지기"), post("/api/v1/wishes/suggestions")).content("{\"content\": \"" + "가".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/wishes/suggestions").content(BODY)).andExpect(status().isUnauthorized());
    }
}
