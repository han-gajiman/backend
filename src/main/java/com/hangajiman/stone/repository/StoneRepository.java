package com.hangajiman.stone.repository;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.stone.domain.Stone;

public interface StoneRepository extends JpaRepository<Stone, Long> {

    // 돌 개수는 COUNT(*)로 셉니다 (CLAUDE.md 규칙 3)
    long countByWishId(Long wishId);

    boolean existsByWishIdAndStoneDate(Long wishId, LocalDate stoneDate);

    Slice<Stone> findByWishId(Long wishId, Pageable pageable);
}
