package com.hangajiman.user.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // 소셜 로그인 시 기존 회원 찾기 (없으면 자동 가입)
    Optional<User> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    // TOWER-04: 첫 돌탑 시작일이 date 이전인 회원 (1년 회고 알림 대상 후보)
    List<User> findByJourneyStartedOnLessThanEqual(LocalDate date);

    // 진행 중(ACTIVE/COMPLETED) 돌탑이 있는지. Wish 엔티티가 생기면 WishRepository로 옮겨도 됩니다.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM wishes WHERE user_id = :userId AND status IN ('ACTIVE', 'COMPLETED'))",
            nativeQuery = true)
    boolean existsOpenWish(@Param("userId") Long userId);

    /**
     * 탈퇴 시 알림 정리. notifications.target_id는 FK가 없어서(CLAUDE.md 규칙 6) 서비스에서 직접 지웁니다.
     * 본인 알림, 본인이 보낸 알림, 본인이 쓴 응원을 가리키는 알림
     */
    @Modifying
    @Query(value = """
            DELETE FROM notifications
            WHERE user_id = :userId
               OR actor_id = :userId
               OR (type = 'CHEER' AND target_id IN (SELECT id FROM cheers WHERE writer_id = :userId))
            """, nativeQuery = true)
    void deleteNotificationsOf(@Param("userId") Long userId);
}
