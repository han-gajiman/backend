package com.hangajiman.notification.domain;

/**
 * 알림 종류와 target_id의 의미 (ERD notifications 주석과 같음)
 * CHEER → cheers.id / NUDGE → 받는 사람의 진행 중 wishes.id / TOWER_COMPLETED → wishes.id
 * POSTCARD → postcard_letters.id (받은 엽서) / YEAR_REVIEW → 회차 번호
 */
public enum NotificationType {
    CHEER,
    NUDGE,
    TOWER_COMPLETED,
    POSTCARD,
    YEAR_REVIEW
}
