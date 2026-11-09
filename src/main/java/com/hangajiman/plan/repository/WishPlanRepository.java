package com.hangajiman.plan.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.plan.domain.WishPlan;

public interface WishPlanRepository extends JpaRepository<WishPlan, Long> {

    List<WishPlan> findByUserIdOrderByStartDateAsc(Long userId);

    // 시작일이 이 범위 안인 메모 (캘린더 한 달 + 앞쪽 29일)
    List<WishPlan> findByUserIdAndStartDateBetweenOrderByStartDateAsc(Long userId, LocalDate from, LocalDate to);

    long countByUserId(Long userId);

    Optional<WishPlan> findByIdAndUserId(Long id, Long userId);
}
