package com.hangajiman.friend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.friend.domain.Cheer;

public interface CheerRepository extends JpaRepository<Cheer, Long> {

    // 돌탑의 응원 목록 (인덱스 idx_cheers_wish)
    Slice<Cheer> findByWishId(Long wishId, Pageable pageable);
}
