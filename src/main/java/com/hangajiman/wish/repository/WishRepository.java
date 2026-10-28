package com.hangajiman.wish.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

public interface WishRepository extends JpaRepository<Wish, Long> {

    // 진행 중(ACTIVE/COMPLETED) 돌탑은 1인 1개 🟡 (DB 부분 유니크 인덱스 uq_wishes_one_open)
    List<WishStatus> OPEN_STATUSES = List.of(WishStatus.ACTIVE, WishStatus.COMPLETED);

    Optional<Wish> findFirstByUserIdAndStatusIn(Long userId, Collection<WishStatus> statuses);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<WishStatus> statuses);

    // TOWER-03 회차별 완성 돌탑 (COMPLETED·CLOSED), 시작일 순
    List<Wish> findByUserIdAndRoundAndStatusInOrderByStartDateAsc(Long userId, int round,
                                                                    Collection<WishStatus> statuses);

    // 여러 돌탑 중 명소 방문 인증을 한 돌탑 id
    @Query(value = "SELECT DISTINCT wish_id FROM spot_visits WHERE wish_id IN (:wishIds)", nativeQuery = true)
    List<Long> findVisitedWishIds(@Param("wishIds") Collection<Long> wishIds);

    // 이 돌탑으로 명소 방문 인증을 했는지 (배경 해금). spot_visits는 SPOT-03에서 채워집니다.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM spot_visits WHERE wish_id = :wishId)", nativeQuery = true)
    boolean existsSpotVisit(@Param("wishId") Long wishId);

    // TOWER-01: 기간이 끝난 진행 중 돌탑의 주인에게 완성 알림 (상태를 바꾸기 전에 호출)
    @Modifying
    @Query(value = """
            INSERT INTO notifications (user_id, type, target_id, message, created_at, updated_at)
            SELECT user_id, 'TOWER_COMPLETED', id, :message, :now, :now
            FROM wishes
            WHERE status = 'ACTIVE' AND end_date < :today
            """, nativeQuery = true)
    int insertCompletedNotifications(@Param("today") LocalDate today, @Param("now") Instant now,
                                     @Param("message") String message);

    // TOWER-01: 기간이 끝난 진행 중 돌탑 → COMPLETED (회고 대기)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wish w SET w.status = com.hangajiman.wish.domain.WishStatus.COMPLETED "
            + "WHERE w.status = com.hangajiman.wish.domain.WishStatus.ACTIVE AND w.endDate < :today")
    int completeExpired(@Param("today") LocalDate today);

    // viewer가 owner의 친구인지. friendships는 FRIEND-01에서 채워집니다(수락 시 양방향 2행).
    @Query(value = "SELECT EXISTS (SELECT 1 FROM friendships WHERE user_id = :viewerId AND friend_id = :ownerId)",
            nativeQuery = true)
    boolean areFriends(@Param("viewerId") Long viewerId, @Param("ownerId") Long ownerId);
}
