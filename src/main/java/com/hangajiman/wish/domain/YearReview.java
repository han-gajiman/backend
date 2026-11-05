package com.hangajiman.wish.domain;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 1년 회고 (ERD year_reviews, V8). 회원당 회차 하나에 한 건이고, 쓴 뒤에는 수정할 수 없습니다.
 */
@Entity
@Table(name = "year_reviews")
public class YearReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false, length = 200)
    private String content;

    protected YearReview() {
    }

    public YearReview(Long userId, int round, String content) {
        this.userId = userId;
        this.round = round;
        this.content = content;
    }

    public int getRound() {
        return round;
    }

    public String getContent() {
        return content;
    }
}
