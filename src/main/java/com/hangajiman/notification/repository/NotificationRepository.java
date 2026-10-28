package com.hangajiman.notification.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.notification.domain.Notification;
import com.hangajiman.notification.domain.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림함 (인덱스 idx_noti_inbox)
    Slice<Notification> findByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    // 콕 찌르기 하루 1회 확인: actor가 user에게 since 이후 보낸 같은 종류 알림 (인덱스 idx_noti_nudge)
    boolean existsByTypeAndActorIdAndUserIdAndCreatedAtGreaterThanEqual(NotificationType type, Long actorId,
                                                                         Long userId, Instant since);
}
