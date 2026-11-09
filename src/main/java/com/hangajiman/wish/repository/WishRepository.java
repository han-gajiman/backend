package com.hangajiman.wish.repository;

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

    // PLAN-01 캘린더: 기간이 [from, to]와 겹치는 내 돌탑 (to 이전에 시작하고 from 이후에 끝남)
    List<Wish> findByUserIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(Long userId,
                                                                                                LocalDate to,
                                                                                                LocalDate from);

    // TOWER-03 회차별 완성 돌탑 (COMPLETED·CLOSED), 시작일 순
    List<Wish> findByUserIdAndRoundAndStatusInOrderByStartDateAsc(Long userId, int round,
                                                                    Collection<WishStatus> statuses);

    interface RoundTowerCount {
        int getRound();

        long getTowerCount();
    }

    // 회차별 완성 돌탑 수
    @Query(value = """
            SELECT round AS round, COUNT(*) AS towerCount
              FROM wishes
             WHERE user_id = :userId AND status <> 'ACTIVE'
             GROUP BY round
            """, nativeQuery = true)
    List<RoundTowerCount> countTowersByRound(@Param("userId") Long userId);

    // 여러 돌탑 중 그 회원이 돌탑의 명소를 방문 인증한 돌탑 id (돌탑 전후 언제 인증했든 상관없음)
    @Query(value = """
            SELECT DISTINCT w.id FROM wishes w
              JOIN spot_visits v ON v.user_id = w.user_id AND v.spot_id = w.spot_id
             WHERE w.id IN (:wishIds)
            """, nativeQuery = true)
    List<Long> findVisitedWishIds(@Param("wishIds") Collection<Long> wishIds);

    // 이 돌탑의 명소를 그 회원이 방문 인증했는지 (배경 해금)
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM wishes w
                JOIN spot_visits v ON v.user_id = w.user_id AND v.spot_id = w.spot_id
               WHERE w.id = :wishId)
            """, nativeQuery = true)
    boolean existsSpotVisit(@Param("wishId") Long wishId);

    // TOWER-01: 기간이 끝난 진행 중 돌탑 (완성 알림 대상)
    List<Wish> findByStatusAndEndDateBefore(WishStatus status, LocalDate date);

    // TOWER-01: 기간이 끝난 진행 중 돌탑 → COMPLETED (회고 대기)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wish w SET w.status = com.hangajiman.wish.domain.WishStatus.COMPLETED "
            + "WHERE w.status = com.hangajiman.wish.domain.WishStatus.ACTIVE AND w.endDate < :today")
    int completeExpired(@Param("today") LocalDate today);
}
