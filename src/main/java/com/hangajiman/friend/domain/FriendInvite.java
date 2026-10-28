package com.hangajiman.friend.domain;

import java.time.Instant;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 친구 초대 링크 (ERD friend_invites). 만료 전까지 여러 명이 수락할 수 있습니다 (친구 인원 제한 없음).
 */
@Entity
@Table(name = "friend_invites")
public class FriendInvite extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected FriendInvite() {
    }

    public FriendInvite(Long inviterId, String inviteCode, Instant expiresAt) {
        this.inviterId = inviterId;
        this.inviteCode = inviteCode;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getInviterId() {
        return inviterId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
