package com.hangajiman.plan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hangajiman.global.response.ApiResponse;
import com.hangajiman.plan.dto.PlanDtos.CalendarResponse;
import com.hangajiman.plan.dto.PlanDtos.CreateTodoRequest;
import com.hangajiman.plan.dto.PlanDtos.CreateWishPlanRequest;
import com.hangajiman.plan.dto.PlanDtos.SuggestTodoRequest;
import com.hangajiman.plan.dto.PlanDtos.TodoItem;
import com.hangajiman.plan.dto.PlanDtos.TodoSuggestionResponse;
import com.hangajiman.plan.dto.PlanDtos.UpdateTodoRequest;
import com.hangajiman.plan.dto.PlanDtos.WishPlanItem;
import com.hangajiman.plan.service.PlanService;

import jakarta.validation.Valid;

// PLAN-01~03 소원 캘린더
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    // PLAN-01 GET /plans/calendar?month=2026-11 (생략하면 이번 달)
    @GetMapping("/calendar")
    public ApiResponse<CalendarResponse> calendar(@AuthenticationPrincipal Long userId,
                                                  @RequestParam(required = false) String month) {
        return ApiResponse.ok(planService.getCalendar(userId, month));
    }

    // PLAN-02 GET /plans/todos/today
    @GetMapping("/todos/today")
    public ApiResponse<List<TodoItem>> todayTodos(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(planService.getTodayTodos(userId));
    }

    // POST /plans/todos → 201
    @PostMapping("/todos")
    public ResponseEntity<ApiResponse<TodoItem>> addTodo(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody CreateTodoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(planService.addTodo(userId, request.date(), request.content())));
    }

    // PATCH /plans/todos/{id} → 204
    @PatchMapping("/todos/{todoId}")
    public ResponseEntity<Void> updateTodo(@AuthenticationPrincipal Long userId, @PathVariable Long todoId,
                                           @RequestBody UpdateTodoRequest request) {
        planService.updateTodo(userId, todoId, request.done());
        return ResponseEntity.noContent().build();
    }

    // DELETE /plans/todos/{id} → 204
    @DeleteMapping("/todos/{todoId}")
    public ResponseEntity<Void> deleteTodo(@AuthenticationPrincipal Long userId, @PathVariable Long todoId) {
        planService.deleteTodo(userId, todoId);
        return ResponseEntity.noContent().build();
    }

    // PLAN-03 POST /plans/todos/suggestion — 한 번에 1개, 하루 10번
    @PostMapping("/todos/suggestion")
    public ApiResponse<TodoSuggestionResponse> suggestTodo(@AuthenticationPrincipal Long userId,
                                                           @Valid @RequestBody SuggestTodoRequest request) {
        return ApiResponse.ok(planService.suggestTodo(userId, request.date()));
    }

    // GET /plans/wish-plans — 새 소원 등록에서 불러오기
    @GetMapping("/wish-plans")
    public ApiResponse<List<WishPlanItem>> wishPlans(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(planService.getWishPlans(userId));
    }

    // POST /plans/wish-plans → 201
    @PostMapping("/wish-plans")
    public ResponseEntity<ApiResponse<WishPlanItem>> addWishPlan(@AuthenticationPrincipal Long userId,
                                                                 @Valid @RequestBody CreateWishPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(planService.addWishPlan(userId, request.content(), request.startDate())));
    }

    // DELETE /plans/wish-plans/{id} → 204
    @DeleteMapping("/wish-plans/{planId}")
    public ResponseEntity<Void> deleteWishPlan(@AuthenticationPrincipal Long userId, @PathVariable Long planId) {
        planService.deleteWishPlan(userId, planId);
        return ResponseEntity.noContent().build();
    }
}
