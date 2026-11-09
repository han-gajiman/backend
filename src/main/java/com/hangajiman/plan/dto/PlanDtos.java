package com.hangajiman.plan.dto;

import java.time.LocalDate;
import java.util.List;

import com.hangajiman.plan.domain.PlanTodo;
import com.hangajiman.plan.domain.WishPlan;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PlanDtos {

    private PlanDtos() {
    }

    // GET /plans/calendar
    public record CalendarResponse(String month, LocalDate today, List<CalendarWish> wishes, List<WishPlanItem> wishPlans,
                                   List<TodoItem> todos) {
    }

    public record CalendarWish(Long wishId, String content, LocalDate startDate, LocalDate endDate, WishStatus status) {
        public static CalendarWish of(Wish wish) {
            return new CalendarWish(wish.getId(), wish.getContent(), wish.getStartDate(), wish.getEndDate(), wish.getStatus());
        }
    }

    public record WishPlanItem(Long planId, String content, LocalDate startDate, LocalDate endDate) {
        public static WishPlanItem of(WishPlan plan) {
            return new WishPlanItem(plan.getId(), plan.getContent(), plan.getStartDate(), plan.getEndDate());
        }
    }

    public record TodoItem(Long todoId, LocalDate date, String content, boolean done) {
        public static TodoItem of(PlanTodo todo) {
            return new TodoItem(todo.getId(), todo.getTodoDate(), todo.getContent(), todo.isDone());
        }
    }

    public record CreateTodoRequest(@NotNull LocalDate date, @NotBlank @Size(max = 40) String content) {
    }

    public record UpdateTodoRequest(Boolean done) {
    }

    public record CreateWishPlanRequest(@NotBlank @Size(max = 100) String content, @NotNull LocalDate startDate) {
    }

    public record SuggestTodoRequest(@NotNull LocalDate date) {
    }

    // fallback: AI가 실패해서 기본 문구, remaining: 오늘 남은 추천 횟수
    public record TodoSuggestionResponse(String content, int remaining, boolean fallback) {
    }
}
