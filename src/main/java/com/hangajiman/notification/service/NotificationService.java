package com.hangajiman.notification.service;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.response.PageResponse;
import com.hangajiman.notification.domain.Notification;
import com.hangajiman.notification.domain.NotificationType;
import com.hangajiman.notification.dto.NotificationItem;
import com.hangajiman.notification.push.NotificationCreatedEvent;
import com.hangajiman.notification.repository.NotificationRepository;

/**
 * NOTI-01 알림. 모든 알림(완성·응원·콕 찌르기 등)은 notify()로 만듭니다.
 * 알림함에 저장한 뒤, 트랜잭션이 커밋되면 Expo 푸시를 보냅니다 (NotificationPushListener).
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository, ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    // 알림 저장 + (커밋 후) 푸시. actorId는 보낸 사람, 시스템 알림이면 null
    @Transactional
    public Notification notify(Long userId, Long actorId, NotificationType type, Long targetId, String message) {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(userId, actorId, type, targetId, message));
        eventPublisher.publishEvent(new NotificationCreatedEvent(userId, type, targetId, message));
        return notification;
    }

    // 콕 찌르기 하루 1회 확인용
    public boolean hasSentSince(NotificationType type, Long actorId, Long userId, Instant since) {
        return notificationRepository.existsByTypeAndActorIdAndUserIdAndCreatedAtGreaterThanEqual(
                type, actorId, userId, since);
    }

    // GET /notifications — 최신순
    public PageResponse<NotificationItem> getNotifications(Long userId, int page, int size) {
        return PageResponse.of(
                notificationRepository.findByUserId(userId, PageResponse.pageable(page, size, LATEST_FIRST)),
                NotificationItem::from);
    }

    // PATCH /notifications/{id}/read — 내 알림이 아니면 404 (있는지 여부를 드러내지 않음)
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND))
                .markRead();
    }
}
