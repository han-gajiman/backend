package com.hangajiman.notification.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.notification.domain.Notification;
import com.hangajiman.notification.domain.NotificationType;

/**
 * GET /notifications 📄 항목 (API 명세서 🔔 Notification)
 */
public record NotificationItem(
        Long id,
        NotificationType type,
        Long targetId,
        String message,
        @JsonProperty("isRead") boolean read,
        OffsetDateTime createdAt
) {

    public static NotificationItem from(Notification n) {
        return new NotificationItem(n.getId(), n.getType(), n.getTargetId(), n.getMessage(), n.isRead(),
                OffsetDateTime.ofInstant(n.getCreatedAt(), ClockConfig.KST));
    }
}
