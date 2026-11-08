package com.hangajiman.spot.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.spot.domain.Spot;

public interface SpotRepository extends JpaRepository<Spot, Long> {

    List<Spot> findAllByOrderByIdAsc();

    List<Spot> findAllByCategoryOrderByIdAsc(String category);

    // 이 회원이 방문 인증한 명소 (지도의 별 표시)
    @Query(value = "SELECT DISTINCT spot_id FROM spot_visits WHERE user_id = :userId", nativeQuery = true)
    List<Long> findVisitedSpotIds(@Param("userId") Long userId);

    // 지금 돌탑(towerId, 없으면 null) 기간에 인증한 명소 → "이 장소 다시 인증하기" 판단
    @Query(value = """
            SELECT DISTINCT spot_id FROM spot_visits
             WHERE user_id = :userId
               AND (wish_id = :towerId OR (CAST(:towerId AS BIGINT) IS NULL AND wish_id IS NULL))
            """, nativeQuery = true)
    List<Long> findSpotIdsVisitedInTower(@Param("userId") Long userId, @Param("towerId") Long towerId);
}
