package com.hangajiman.spot.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.spot.domain.SpotVisit;

public interface SpotVisitRepository extends JpaRepository<SpotVisit, Long> {

    Optional<SpotVisit> findByUserIdAndSpotId(Long userId, Long spotId);

    List<SpotVisit> findByUserIdOrderByVerifiedAtAsc(Long userId);
}
