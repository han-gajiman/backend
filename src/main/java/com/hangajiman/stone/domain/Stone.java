package com.hangajiman.stone.domain;

import java.time.LocalDate;

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
 * 돌 = 하루 1개 기록 (ERD stones). UNIQUE(wish_id, stone_date)로 하루 1개를 보장합니다.
 */
@Entity
@Table(name = "stones")
public class Stone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wish_id", nullable = false)
    private Long wishId;

    // KST 기준 날짜
    @Column(name = "stone_date", nullable = false)
    private LocalDate stoneDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoneType type;

    // PRACTICE일 때 실천 기록
    @Column(length = 100)
    private String content;

    protected Stone() {
    }

    private Stone(Long wishId, LocalDate stoneDate, String content) {
        this.wishId = wishId;
        this.stoneDate = stoneDate;
        this.type = (content == null || content.isBlank()) ? StoneType.ATTENDANCE : StoneType.PRACTICE;
        this.content = type == StoneType.PRACTICE ? content : null;
    }

    // content가 있으면 실천 기록, 없으면 출석
    public static Stone of(Long wishId, LocalDate stoneDate, String content) {
        return new Stone(wishId, stoneDate, content);
    }

    public Long getId() {
        return id;
    }

    public Long getWishId() {
        return wishId;
    }

    public LocalDate getStoneDate() {
        return stoneDate;
    }

    public StoneType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }
}
