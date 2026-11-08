package com.hangajiman.postcard.domain;

import java.time.Instant;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * SPOT-04 모은 엽서 (ERD postcards). 회원·명소·스타일(1~4)마다 한 장. 방문 인증할 때 받습니다.
 */
@Entity
@Table(name = "postcards")
public class Postcard extends BaseEntity {

    public static final int STYLES_PER_SPOT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(name = "visit_id", nullable = false)
    private Long visitId;

    @Column(nullable = false)
    private short style;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected Postcard() {
    }

    public Postcard(Long userId, Long spotId, Long visitId, int style, Instant issuedAt) {
        this.userId = userId;
        this.spotId = spotId;
        this.visitId = visitId;
        this.style = (short) style;
        this.issuedAt = issuedAt;
    }

    public Long getSpotId() {
        return spotId;
    }

    public int getStyle() {
        return style;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
