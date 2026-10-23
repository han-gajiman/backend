package com.hangajiman.spot.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.spot.domain.Spot;

public interface SpotRepository extends JpaRepository<Spot, Long> {
}
