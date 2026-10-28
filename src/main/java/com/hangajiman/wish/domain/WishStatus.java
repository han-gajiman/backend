package com.hangajiman.wish.domain;

/**
 * ACTIVE    진행 중 (end_date 이전)
 * COMPLETED 30일 경과, 회고 대기 (스케줄러가 전환)
 * CLOSED    회고 완료
 */
public enum WishStatus {
    ACTIVE,
    COMPLETED,
    CLOSED
}
