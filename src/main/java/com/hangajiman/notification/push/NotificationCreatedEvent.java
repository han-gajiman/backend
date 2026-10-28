package com.hangajiman.notification.push;

import com.hangajiman.notification.domain.NotificationType;

// 알림이 저장됐다는 이벤트. 트랜잭션이 커밋된 뒤 NotificationPushListener가 푸시를 보냅니다.
public record NotificationCreatedEvent(Long userId, NotificationType type, Long targetId, String message) {
}
