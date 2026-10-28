package com.hangajiman.wish.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
 * 소원 = 돌탑 1개, 30일 (ERD wishes).
 * content·actionGoal은 등록 후 수정할 수 없습니다(CLAUDE.md 규칙 2) → setter를 두지 않습니다.
 */
@Entity
@Table(name = "wishes")
public class Wish extends BaseEntity {

    public static final int TOWER_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(nullable = false, length = 100, updatable = false)
    private String content;

    @Column(name = "action_goal", nullable = false, length = 100, updatable = false)
    private String actionGoal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WishStatus status;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    // 회차: 완성일(end_date) 기준. User.roundOf(endDate)
    @Column(nullable = false)
    private int round;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WishResult result;

    @Column(length = 200)
    private String reflection;

    protected Wish() {
    }

    private Wish(Long userId, Long spotId, String content, String actionGoal, Visibility visibility,
                 LocalDate startDate, int round) {
        this.userId = userId;
        this.spotId = spotId;
        this.content = content;
        this.actionGoal = actionGoal;
        this.visibility = visibility;
        this.status = WishStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDateOf(startDate);
        this.round = round;
    }

    // WISH-03 등록. startDate는 주입된 Clock 기준 오늘(KST)
    public static Wish create(Long userId, Long spotId, String content, String actionGoal, Visibility visibility,
                              LocalDate startDate, int round) {
        return new Wish(userId, spotId, content, actionGoal, visibility, startDate, round);
    }

    // 30일째 = 시작일 + 29
    public static LocalDate endDateOf(LocalDate startDate) {
        return startDate.plusDays(TOWER_DAYS - 1);
    }

    // 며칠째인지 (1~30). 기간이 지나도 30
    public int dayIndexOn(LocalDate today) {
        long days = ChronoUnit.DAYS.between(startDate, today) + 1;
        return (int) Math.max(1, Math.min(days, TOWER_DAYS));
    }

    public int daysLeftOn(LocalDate today) {
        return TOWER_DAYS - dayIndexOn(today);
    }

    // 돌을 쌓을 수 있는지: 진행 중이고 날짜가 기간 안 (스케줄러가 아직 안 돌았어도 기간 밖이면 불가)
    public boolean isStackableOn(LocalDate date) {
        return status == WishStatus.ACTIVE && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSpotId() {
        return spotId;
    }

    public String getContent() {
        return content;
    }

    public String getActionGoal() {
        return actionGoal;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public WishStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getRound() {
        return round;
    }

    public WishResult getResult() {
        return result;
    }

    public String getReflection() {
        return reflection;
    }
}
