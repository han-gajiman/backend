package com.hangajiman.wish.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

public interface WishRepository extends JpaRepository<Wish, Long> {

    // 진행 중(ACTIVE/COMPLETED) 돌탑은 1인 1개 🟡 (DB 부분 유니크 인덱스 uq_wishes_one_open)
    List<WishStatus> OPEN_STATUSES = List.of(WishStatus.ACTIVE, WishStatus.COMPLETED);

    Optional<Wish> findFirstByUserIdAndStatusIn(Long userId, Collection<WishStatus> statuses);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<WishStatus> statuses);

    // 이 돌탑으로 명소 방문 인증을 했는지 (배경 해금). spot_visits는 SPOT-03에서 채워집니다.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM spot_visits WHERE wish_id = :wishId)", nativeQuery = true)
    boolean existsSpotVisit(@Param("wishId") Long wishId);

    // viewer가 owner의 친구인지. friendships는 FRIEND-01에서 채워집니다(수락 시 양방향 2행).
    @Query(value = "SELECT EXISTS (SELECT 1 FROM friendships WHERE user_id = :viewerId AND friend_id = :ownerId)",
            nativeQuery = true)
    boolean areFriends(@Param("viewerId") Long viewerId, @Param("ownerId") Long ownerId);
}
