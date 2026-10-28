package com.hangajiman.friend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.friend.domain.Friendship;
import com.hangajiman.wish.domain.Wish;
import com.hangajiman.wish.domain.WishStatus;

/**
 * 친구 관계 + 친구 기능에서 필요한 돌탑·돌 읽기 조회.
 * Wish·Stone 엔티티는 wish·stone 도메인 것을 읽기만 합니다 (그쪽 리포지토리는 수정하지 않음).
 */
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    // 내 친구 목록 (hasNext만 필요해서 count 쿼리 없는 Slice)
    Slice<Friendship> findByUserId(Long userId, Pageable pageable);

    // 여러 회원의 특정 상태 돌탑 (친구 목록·콕 찌르기)
    @Query("SELECT w FROM Wish w WHERE w.userId IN :userIds AND w.status = :status")
    List<Wish> findWishesOf(@Param("userIds") Collection<Long> userIds, @Param("status") WishStatus status);

    // 여러 돌탑의 돌 개수 (COUNT, CLAUDE.md 규칙 3)
    @Query("SELECT s.wishId AS wishId, COUNT(s) AS stoneCount FROM Stone s WHERE s.wishId IN :wishIds GROUP BY s.wishId")
    List<WishStoneCount> countStones(@Param("wishIds") Collection<Long> wishIds);

    // 여러 돌탑 중 해당 날짜(KST)에 돌을 쌓은 돌탑 id
    @Query("SELECT s.wishId FROM Stone s WHERE s.wishId IN :wishIds AND s.stoneDate = :stoneDate")
    List<Long> findWishIdsStonedOn(@Param("wishIds") Collection<Long> wishIds, @Param("stoneDate") LocalDate stoneDate);

    interface WishStoneCount {
        Long getWishId();

        Long getStoneCount();
    }
}
