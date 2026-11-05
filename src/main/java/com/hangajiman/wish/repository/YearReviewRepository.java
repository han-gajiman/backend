package com.hangajiman.wish.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.wish.domain.YearReview;

public interface YearReviewRepository extends JpaRepository<YearReview, Long> {

    List<YearReview> findByUserId(Long userId);

    boolean existsByUserIdAndRound(Long userId, int round);
}
