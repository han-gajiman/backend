package com.hangajiman.auth.domain;

import java.time.Instant;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 비밀번호 찾기 인증코드 (ERD password_reset_codes, V6). 코드 원문은 저장하지 않고 BCrypt 해시만 저장합니다.
 * 만료와 재전송 간격은 실제 시간 기준입니다. (시연용 시간 이동을 따라가지 않음)
 */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode extends BaseEntity {

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    protected PasswordResetCode() {
    }

    public PasswordResetCode(Long userId, String codeHash, Instant expiresAt) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    // 만료됐거나 너무 많이 틀렸으면 더 이상 쓸 수 없음
    public boolean isUsable(Instant now) {
        return expiresAt.isAfter(now) && attempts < MAX_ATTEMPTS;
    }

    public void recordFailedAttempt() {
        attempts++;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
