package com.hangajiman.plan.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.ai.client.WishSuggester;
import com.hangajiman.ai.domain.AiSuggestionLog;
import com.hangajiman.ai.repository.AiSuggestionLogRepository;
import com.hangajiman.ai.service.WishSuggestionService;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.plan.domain.PlanTodo;
import com.hangajiman.plan.domain.WishPlan;
import com.hangajiman.plan.dto.PlanDtos.CalendarResponse;
import com.hangajiman.plan.dto.PlanDtos.CalendarWish;
import com.hangajiman.plan.dto.PlanDtos.TodoItem;
import com.hangajiman.plan.dto.PlanDtos.TodoSuggestionResponse;
import com.hangajiman.plan.dto.PlanDtos.WishPlanItem;
import com.hangajiman.plan.repository.PlanTodoRepository;
import com.hangajiman.plan.repository.WishPlanRepository;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;
import com.hangajiman.wish.repository.WishRepository;

/**
 * PLAN-01~03 소원 캘린더. 날짜는 모두 주입된 Clock(KST, 시연 시간 이동 반영) 기준입니다.
 * - 할 일: 날짜마다 5개, 지난 날짜는 바꿀 수 없음
 * - 소원 메모: 30일 동안 소원은 하나라서 진행 중 돌탑 기간·다른 메모의 30일과 겹치지 않는 날짜만
 * - AI 할 일 추천: 진행 중 돌탑 기간의 날짜만, 한 번에 1개, 하루 10번(소원 추천과 따로)
 */
@Service
@Transactional(readOnly = true)
public class PlanService {

    public static final int TODOS_PER_DAY = 5;
    public static final int WISH_PLAN_LIMIT = 12;
    public static final int SUGGEST_DAILY_LIMIT = 10;
    private static final int TODO_MAX_LENGTH = 40;

    // AI가 실패하면 번갈아 쓰는 기본 문구
    static final List<String> DEFAULT_TODOS = List.of(
            "실천 목표를 10분만 먼저 해 보기",
            "오늘 할 일 하나를 아침에 끝내기",
            "어제 한 것을 5분 동안 돌아보기");

    // 다시 추천할 때마다 바꾸는 방향 (오늘 몇 번째 추천인지에 따라 돌아감)
    static final List<String> ANGLES = List.of(
            "실천 목표를 오늘 바로 하는 작은 행동",
            "준비하거나 환경을 만드는 행동",
            "한 것을 기록하거나 돌아보는 행동",
            "조금 더 도전적인 행동",
            "몸과 마음을 회복하는 행동",
            "누군가와 함께하거나 알리는 행동",
            "배우거나 찾아보는 행동");

    private final PlanTodoRepository todoRepository;
    private final WishPlanRepository wishPlanRepository;
    private final WishRepository wishRepository;
    private final AiSuggestionLogRepository logRepository;
    private final WishSuggester suggester;
    private final Clock clock;

    public PlanService(PlanTodoRepository todoRepository, WishPlanRepository wishPlanRepository,
                       WishRepository wishRepository, AiSuggestionLogRepository logRepository,
                       WishSuggester suggester, Clock clock) {
        this.todoRepository = todoRepository;
        this.wishPlanRepository = wishPlanRepository;
        this.wishRepository = wishRepository;
        this.logRepository = logRepository;
        this.suggester = suggester;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // PLAN-01 GET /plans/calendar?month=YYYY-MM (없으면 이번 달): 그 달과 겹치는 돌탑·소원 메모·할 일
    public CalendarResponse getCalendar(Long userId, String month) {
        YearMonth ym;
        try {
            ym = month == null || month.isBlank() ? YearMonth.from(today()) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        List<CalendarWish> wishes = wishRepository
                .findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(userId, last, first)
                .stream().map(CalendarWish::of).toList();
        // 메모는 30일짜리라, 이 달 시작 29일 전부터 시작한 메모도 이 달에 걸칩니다
        List<WishPlanItem> plans = wishPlanRepository
                .findByUserIdAndStartDateBetweenOrderByStartDateAsc(userId, first.minusDays(WishPlan.DAYS - 1), last)
                .stream().map(WishPlanItem::of).toList();
        List<TodoItem> todos = todoRepository
                .findByUserIdAndTodoDateBetweenOrderByTodoDateAscIdAsc(userId, first, last)
                .stream().map(TodoItem::of).toList();
        return new CalendarResponse(ym.toString(), today(), wishes, plans, todos);
    }

    // PLAN-02 GET /plans/todos/today
    public List<TodoItem> getTodayTodos(Long userId) {
        return todoRepository.findByUserIdAndTodoDateOrderByIdAsc(userId, today()).stream().map(TodoItem::of).toList();
    }

    @Transactional
    public TodoItem addTodo(Long userId, LocalDate date, String content) {
        requireNotPast(date);
        if (todoRepository.countByUserIdAndTodoDate(userId, date) >= TODOS_PER_DAY) {
            throw new BusinessException(ErrorCode.PLAN_TODO_LIMIT);
        }
        return TodoItem.of(todoRepository.save(new PlanTodo(userId, date, content.trim())));
    }

    @Transactional
    public void updateTodo(Long userId, Long todoId, Boolean done) {
        PlanTodo todo = findTodo(userId, todoId);
        requireNotPast(todo.getTodoDate());
        if (done != null) {
            todo.setDone(done);
        }
    }

    @Transactional
    public void deleteTodo(Long userId, Long todoId) {
        PlanTodo todo = findTodo(userId, todoId);
        requireNotPast(todo.getTodoDate());
        todoRepository.delete(todo);
    }

    // 돌 쌓을 때 기록에 넣은 오늘 할 일을 완료로 (내 것·오늘 것만, 나머지 id는 무시)
    @Transactional
    public void markDoneToday(Long userId, List<Long> todoIds) {
        if (todoIds != null && !todoIds.isEmpty()) {
            todoRepository.markDone(userId, today(), todoIds);
        }
    }

    // PLAN-01 소원 메모
    public List<WishPlanItem> getWishPlans(Long userId) {
        return wishPlanRepository.findByUserIdOrderByStartDateAsc(userId).stream().map(WishPlanItem::of).toList();
    }

    @Transactional
    public WishPlanItem addWishPlan(Long userId, String content, LocalDate startDate) {
        requireNotPast(startDate);
        Optional<Wish> open = wishRepository.findFirstByUserIdAndStatusIn(userId, WishRepository.OPEN_STATUSES);
        if (open.isPresent() && !startDate.isAfter(open.get().getEndDate())) {
            throw new BusinessException(ErrorCode.PLAN_WISH_IN_TOWER);
        }
        LocalDate endDate = startDate.plusDays(WishPlan.DAYS - 1);
        List<WishPlan> mine = wishPlanRepository.findByUserIdOrderByStartDateAsc(userId);
        if (mine.stream().anyMatch(p -> !p.getStartDate().isAfter(endDate) && !p.getEndDate().isBefore(startDate))) {
            throw new BusinessException(ErrorCode.PLAN_WISH_OVERLAP);
        }
        if (mine.size() >= WISH_PLAN_LIMIT) {
            throw new BusinessException(ErrorCode.PLAN_WISH_LIMIT);
        }
        return WishPlanItem.of(wishPlanRepository.save(new WishPlan(userId, content.trim(), startDate)));
    }

    @Transactional
    public void deleteWishPlan(Long userId, Long planId) {
        WishPlan plan = wishPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_WISH_NOT_FOUND));
        wishPlanRepository.delete(plan);
    }

    // PLAN-03 POST /plans/todos/suggestion
    @Transactional
    public TodoSuggestionResponse suggestTodo(Long userId, LocalDate date) {
        Wish wish = wishRepository.findFirstByUserIdAndStatusIn(userId, List.of(WishStatus.ACTIVE))
                .filter(w -> !date.isBefore(w.getStartDate()) && !date.isAfter(w.getEndDate()) && !date.isBefore(today()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_SUGGEST_OUT_OF_TOWER));

        Instant todayStart = today().atStartOfDay(clock.getZone()).toInstant();
        long used = logRepository.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, AiSuggestionLog.TODO, todayStart);
        if (used >= SUGGEST_DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT);
        }
        logRepository.save(new AiSuggestionLog(userId, AiSuggestionLog.TODO));
        int remaining = (int) (SUGGEST_DAILY_LIMIT - used - 1);

        List<String> existing = todoRepository.findByUserIdAndTodoDateOrderByIdAsc(userId, date).stream()
                .map(PlanTodo::getContent).toList();
        int dayIndex = (int) ChronoUnit.DAYS.between(wish.getStartDate(), date) + 1;
        // 위기 단어가 있는 소원은 AI에 보내지 않고 기본 문구
        boolean crisis = WishSuggestionService.CRISIS_WORDS.stream().anyMatch(wish.getContent()::contains);
        Optional<String> todo = (crisis ? Optional.<String>empty()
                : suggester.suggestTodo(wish.getContent(), wish.getActionGoal(), dayIndex, existing,
                        ANGLES.get((int) (used % ANGLES.size()))))
                .map(String::trim)
                .filter(t -> !t.isEmpty() && t.length() <= TODO_MAX_LENGTH && !existing.contains(t));
        if (todo.isPresent()) {
            return new TodoSuggestionResponse(todo.get(), remaining, false);
        }
        String fallback = DEFAULT_TODOS.stream().filter(t -> !existing.contains(t)).findFirst().orElse(DEFAULT_TODOS.get(0));
        return new TodoSuggestionResponse(fallback, remaining, true);
    }

    private PlanTodo findTodo(Long userId, Long todoId) {
        return todoRepository.findByIdAndUserId(todoId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_TODO_NOT_FOUND));
    }

    private void requireNotPast(LocalDate date) {
        if (date.isBefore(today())) {
            throw new BusinessException(ErrorCode.PLAN_PAST_DATE);
        }
    }
}
