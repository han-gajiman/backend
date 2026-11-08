package com.hangajiman.postcard.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.postcard.domain.Postcard;

public interface PostcardRepository extends JpaRepository<Postcard, Long> {

    List<Postcard> findByUserIdOrderBySpotIdAscStyleAsc(Long userId);

    List<Postcard> findByUserIdAndSpotId(Long userId, Long spotId);

    boolean existsByUserIdAndSpotIdAndStyle(Long userId, Long spotId, short style);
}
