package com.hangajiman.notification.domain;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 알림함 (ERD notifications). 앱 내 알림 목록이자 푸시 발송 기록입니다.
 * target_id는 FK가 없어 탈퇴 시 서비스에서 정리합니다 (CLAUDE.md 규칙 6).
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 받는 사람
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 보낸 사람 (응원·콕 찌르기). 시스템 알림은 null
    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    protected Notification() {
    }

    public Notification(Long userId, Long actorId, NotificationType type, Long targetId, String message) {
        this.userId = userId;
        this.actorId = actorId;
        this.type = type;
        this.targetId = targetId;
        this.message = message;
    }

    public void markRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getActorId() {
        return actorId;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }
}
