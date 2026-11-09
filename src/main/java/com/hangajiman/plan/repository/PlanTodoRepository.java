package com.hangajiman.plan.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.hangajiman.plan.domain.PlanTodo;

public interface PlanTodoRepository extends JpaRepository<PlanTodo, Long> {

    List<PlanTodo> findByUserIdAndTodoDateBetweenOrderByTodoDateAscIdAsc(Long userId, LocalDate from, LocalDate to);

    List<PlanTodo> findByUserIdAndTodoDateOrderByIdAsc(Long userId, LocalDate date);

    long countByUserIdAndTodoDate(Long userId, LocalDate date);

    Optional<PlanTodo> findByIdAndUserId(Long id, Long userId);

    // 돌 쌓을 때 기록에 넣은 오늘 할 일을 완료로 (내 것, 오늘 것만)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PlanTodo t SET t.done = true WHERE t.userId = :userId AND t.todoDate = :date AND t.id IN :ids")
    int markDone(Long userId, LocalDate date, Collection<Long> ids);
}
