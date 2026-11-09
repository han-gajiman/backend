package com.hangajiman.plan.domain;

import java.time.LocalDate;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 앞으로 할 소원 메모 (ERD wish_plans, V12). 실제 돌탑(wishes)이 아니라 메모라 지울 수 있고,
 * 새 소원을 등록할 때 불러옵니다. 시작 예정일부터 30일을 캘린더에 점선으로 보여줍니다.
 */
@Entity
@Table(name = "wish_plans")
public class WishPlan extends BaseEntity {

    public static final int DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    protected WishPlan() {
    }

    public WishPlan(Long userId, String content, LocalDate startDate) {
        this.userId = userId;
        this.content = content;
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return startDate.plusDays(DAYS - 1);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public LocalDate getStartDate() {
        return startDate;
    }
}
