package com.hangajiman.spot.domain;

import java.time.Instant;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 명소 방문 인증 (ERD spot_visits). 같은 명소는 돌탑(진행 중 소원)마다 한 건입니다 (V9).
 * 위치는 앱이 기기 안에서 확인하고 서버로 보내지 않으므로 좌표·거리는 없습니다.
 */
@Entity
@Table(name = "spot_visits")
public class SpotVisit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(name = "wish_id")
    private Long wishId; // 인증할 때 진행 중이던 돌탑 (없으면 null)

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    protected SpotVisit() {
    }

    public SpotVisit(Long userId, Long spotId, Long wishId, Instant verifiedAt) {
        this.userId = userId;
        this.spotId = spotId;
        this.wishId = wishId;
        this.verifiedAt = verifiedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSpotId() {
        return spotId;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }
}
