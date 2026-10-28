package com.hangajiman.friend.repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 응원·콕 찌르기 알림을 알림함(ERD notifications)에 저장합니다.
 * notification 도메인(엔티티·알림 목록·Expo 푸시, NOTI-01)은 담당자가 따로 만들기 때문에
 * 친구 기능은 엔티티 없이 이 테이블에 행만 남깁니다. 시각은 주입된 Clock 기준 (CLAUDE.md 규칙 1)
 */
@Repository
public class FriendNotificationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public FriendNotificationRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    // CHEER: target_id = cheers.id
    public void saveCheer(Long receiverId, Long writerId, Long cheerId, String message) {
        insert(receiverId, writerId, "CHEER", cheerId, message);
    }

    // NUDGE: target_id = 받는 사람의 진행 중 돌탑 wishes.id
    public void saveNudge(Long receiverId, Long senderId, Long wishId, String message) {
        insert(receiverId, senderId, "NUDGE", wishId, message);
    }

    // sender가 receiver에게 since 이후 콕 찌른 적 있는지 (인덱스 idx_noti_nudge)
    public boolean existsNudgeSince(Long senderId, Long receiverId, Instant since) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM notifications
                               WHERE type = 'NUDGE' AND actor_id = ? AND user_id = ? AND created_at >= ?)
                """, Boolean.class, senderId, receiverId, Timestamp.from(since));
        return Boolean.TRUE.equals(exists);
    }

    private void insert(Long receiverId, Long actorId, String type, Long targetId, String message) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, actor_id, type, target_id, message, is_read, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, false, ?, ?)
                """, receiverId, actorId, type, targetId, message, now, now);
    }
}
