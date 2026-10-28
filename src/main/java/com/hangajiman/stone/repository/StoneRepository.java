package com.hangajiman.stone.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.stone.domain.Stone;

public interface StoneRepository extends JpaRepository<Stone, Long> {

    // 돌 개수는 COUNT(*)로 셉니다 (CLAUDE.md 규칙 3)
    long countByWishId(Long wishId);

    boolean existsByWishIdAndStoneDate(Long wishId, LocalDate stoneDate);

    Slice<Stone> findByWishId(Long wishId, Pageable pageable);

    // 여러 돌탑의 돌 개수를 한 번에 (모아보기). 돌이 0개인 돌탑은 결과에 없습니다.
    @Query("SELECT s.wishId AS wishId, COUNT(s) AS stoneCount FROM Stone s "
            + "WHERE s.wishId IN :wishIds GROUP BY s.wishId")
    List<StoneCount> countByWishIds(@Param("wishIds") Collection<Long> wishIds);

    interface StoneCount {
        Long getWishId();

        long getStoneCount();
    }
}
