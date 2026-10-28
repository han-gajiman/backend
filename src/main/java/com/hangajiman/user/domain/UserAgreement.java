package com.hangajiman.user.domain;

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
 * 약관·위치정보 동의 이력 (ERD user_agreements).
 * 바꿀 때마다 행을 새로 추가하고, 같은 type의 가장 최근 행이 현재 상태입니다.
 */
@Entity
@Table(name = "user_agreements")
public class UserAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgreementType type;

    @Column(nullable = false, length = 10)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    protected UserAgreement() {
    }

    public UserAgreement(Long userId, AgreementType type, String version, boolean agreed) {
        this.userId = userId;
        this.type = type;
        this.version = version;
        this.agreed = agreed;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AgreementType getType() {
        return type;
    }

    public String getVersion() {
        return version;
    }

    public boolean isAgreed() {
        return agreed;
    }
}
