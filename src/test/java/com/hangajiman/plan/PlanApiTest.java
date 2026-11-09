package com.hangajiman.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hangajiman.ai.client.WishSuggester;
import com.hangajiman.friend.FriendApiTestSupport;
import com.hangajiman.user.domain.User;
import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.Wish;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// PLAN-01~03 소원 캘린더. 실제 Claude는 부르지 않습니다.
class PlanApiTest extends FriendApiTestSupport {

    @MockBean
    private WishSuggester suggester;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private String todo(LocalDate date, String content) {
        return "{\"date\": \"" + date + "\", \"content\": \"" + content + "\"}";
    }

    private int addTodo(User user, LocalDate date, String content) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/v1/plans/todos")).content(todo(date, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.todoId");
    }

    private String plan(LocalDate start, String content) {
        return "{\"startDate\": \"" + start + "\", \"content\": \"" + content + "\"}";
    }

    @Test
    void calendarShowsTowerPlansAndTodosOfMonth() throws Exception {
        User me = user("돌쌓기");
        Wish wish = activeWish(me, Visibility.PRIVATE);
        addTodo(me, today(), "단어 20개 외우기");
        mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(wish.getEndDate().plusDays(1), "하프 마라톤")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endDate").value(wish.getEndDate().plusDays(30).toString()));

        String month = today().toString().substring(0, 7);
        mockMvc.perform(as(me, get("/api/v1/plans/calendar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(month))
                .andExpect(jsonPath("$.data.today").value(today().toString()))
                .andExpect(jsonPath("$.data.wishes[0].wishId").value(wish.getId()))
                .andExpect(jsonPath("$.data.wishes[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.todos[0].content").value("단어 20개 외우기"))
                .andExpect(jsonPath("$.data.todos[0].done").value(false));

        // 다음 달에도 30일짜리 돌탑과 메모가 걸쳐 보임
        String next = today().plusMonths(1).toString().substring(0, 7);
        mockMvc.perform(as(me, get("/api/v1/plans/calendar").param("month", next)))
                .andExpect(jsonPath("$.data.month").value(next))
                .andExpect(jsonPath("$.data.wishPlans[0].content").value("하프 마라톤"))
                .andExpect(jsonPath("$.data.todos.length()").value(0));

        mockMvc.perform(as(me, get("/api/v1/plans/calendar").param("month", "2026-13")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void todoLimitPastDateAndOwnership() throws Exception {
        User me = user("돌쌓기");
        User other = user("남");
        for (int i = 0; i < 5; i++) {
            addTodo(me, today().plusDays(1), "할 일 " + i);
        }
        mockMvc.perform(as(me, post("/api/v1/plans/todos")).content(todo(today().plusDays(1), "여섯 번째")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLAN_TODO_LIMIT"));
        mockMvc.perform(as(me, post("/api/v1/plans/todos")).content(todo(today().minusDays(1), "어제 일")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLAN_PAST_DATE"));
        mockMvc.perform(as(me, post("/api/v1/plans/todos")).content(todo(today(), "a".repeat(41))))
                .andExpect(status().isBadRequest());

        int id = addTodo(me, today(), "체크할 일");
        mockMvc.perform(as(other, patch("/api/v1/plans/todos/{id}", id)).content("{\"done\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLAN_TODO_NOT_FOUND"));
        mockMvc.perform(as(me, patch("/api/v1/plans/todos/{id}", id)).content("{\"done\": true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(me, get("/api/v1/plans/todos/today")))
                .andExpect(jsonPath("$.data[0].done").value(true));
        mockMvc.perform(as(me, delete("/api/v1/plans/todos/{id}", id))).andExpect(status().isNoContent());
        mockMvc.perform(as(me, get("/api/v1/plans/todos/today"))).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void wishPlanOnlyAfterTowerAndWithoutOverlap() throws Exception {
        User me = user("돌쌓기");
        Wish wish = activeWish(me, Visibility.PRIVATE);
        LocalDate afterTower = wish.getEndDate().plusDays(1);

        mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(today().plusDays(3), "진행 중 기간")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLAN_WISH_IN_TOWER"));
        String body = mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(afterTower, "다음 소원")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(afterTower.plusDays(29), "겹치는 소원")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLAN_WISH_OVERLAP"));
        mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(afterTower.plusDays(30), "그다음 소원")))
                .andExpect(status().isCreated());

        mockMvc.perform(as(me, get("/api/v1/plans/wish-plans")))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("다음 소원"));
        Integer planId = JsonPath.read(body, "$.data.planId");
        mockMvc.perform(as(user("남"), delete("/api/v1/plans/wish-plans/{id}", planId))).andExpect(status().isNotFound());
        mockMvc.perform(as(me, delete("/api/v1/plans/wish-plans/{id}", planId))).andExpect(status().isNoContent());
    }

    @Test
    void suggestOnlyInActiveTowerTenTimesSeparateFromWishSuggestions() throws Exception {
        User me = user("돌쌓기");
        Wish wish = activeWish(me, Visibility.PRIVATE);
        when(suggester.suggestTodo(anyString(), anyString(), anyInt(), anyList(), anyString())).thenReturn(Optional.of(" 단어 20개 소리 내어 외우기 "));

        mockMvc.perform(as(me, post("/api/v1/plans/todos/suggestion")).content("{\"date\": \"" + wish.getEndDate().plusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PLAN_SUGGEST_OUT_OF_TOWER"));

        String date = "{\"date\": \"" + today().plusDays(2) + "\"}";
        mockMvc.perform(as(me, post("/api/v1/plans/todos/suggestion")).content(date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("단어 20개 소리 내어 외우기"))
                .andExpect(jsonPath("$.data.remaining").value(9))
                .andExpect(jsonPath("$.data.fallback").value(false));
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(as(me, post("/api/v1/plans/todos/suggestion")).content(date)).andExpect(status().isOk());
        }
        mockMvc.perform(as(me, post("/api/v1/plans/todos/suggestion")).content(date))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AI_RATE_LIMIT"));

        // 소원 등록 AI 추천은 따로 셈
        mockMvc.perform(as(me, post("/api/v1/wishes/suggestions")).content("{\"content\": \"취업하기\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void suggestFallsBackWhenAiFails() throws Exception {
        User me = user("돌쌓기");
        activeWish(me, Visibility.PRIVATE);
        when(suggester.suggestTodo(anyString(), anyString(), anyInt(), anyList(), anyString())).thenReturn(Optional.of("가".repeat(41)));

        mockMvc.perform(as(me, post("/api/v1/plans/todos/suggestion")).content("{\"date\": \"" + today() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    void stackingMarksPickedTodayTodosDone() throws Exception {
        User me = user("돌쌓기");
        User other = user("남");
        Wish wish = activeWish(me, Visibility.PRIVATE);
        int picked = addTodo(me, today(), "단어 외우기");
        int notPicked = addTodo(me, today(), "문제 풀기");
        int tomorrow = addTodo(me, today().plusDays(1), "내일 할 일");
        int othersTodo = addTodo(other, today(), "남의 할 일");

        mockMvc.perform(as(me, post("/api/v1/wishes/{id}/stones", wish.getId()))
                        .content("{\"content\": \"단어 외우기\", \"doneTodoIds\": [" + picked + ", " + tomorrow + ", " + othersTodo + "]}"))
                .andExpect(status().isCreated());

        assertThat(done(picked)).isTrue();
        assertThat(done(notPicked)).isFalse();
        assertThat(done(tomorrow)).isFalse(); // 오늘 것만
        assertThat(done(othersTodo)).isFalse(); // 내 것만
    }

    @Test
    void withdrawalRemovesPlans() throws Exception {
        User me = user("돌쌓기");
        addTodo(me, today(), "할 일");
        mockMvc.perform(as(me, post("/api/v1/plans/wish-plans")).content(plan(today().plusDays(1), "다음 소원")))
                .andExpect(status().isCreated());

        mockMvc.perform(as(me, delete("/api/v1/users/me"))).andExpect(status().isNoContent());
        entityManager.flush(); // 회원 삭제를 DB에 반영해야 CASCADE가 일어남 (테스트는 끝에 롤백)

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM plan_todos WHERE user_id = ?", Integer.class, me.getId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wish_plans WHERE user_id = ?", Integer.class, me.getId()))
                .isZero();
    }

    private boolean done(int todoId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT done FROM plan_todos WHERE id = ?", Boolean.class, todoId));
    }
}
