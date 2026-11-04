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
}
