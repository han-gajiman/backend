package com.hangajiman.notification.push;

import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

/**
 * 알림이 커밋된 뒤 별도 스레드에서 Expo 푸시를 보냅니다. (응원·콕 찌르기 응답이 푸시 때문에 늦어지지 않게)
 * 푸시 토큰이 없으면 보내지 않고, Expo가 "더 이상 받을 수 없는 기기"라고 하면 토큰을 지웁니다.
 */
@Component
public class NotificationPushListener {

    private final UserRepository userRepository;
    private final ExpoPushClient expoPushClient;

    public NotificationPushListener(UserRepository userRepository, ExpoPushClient expoPushClient) {
        this.userRepository = userRepository;
        this.expoPushClient = expoPushClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null || !ExpoPushClient.isExpoToken(user.getPushToken())) {
            return;
        }

        ExpoPushClient.Result result = expoPushClient.send(user.getPushToken(), event.message(),
                Map.of("type", event.type().name(), "targetId", event.targetId()));
        if (result == ExpoPushClient.Result.DEVICE_NOT_REGISTERED) {
            user.updatePushToken(null);
        }
    }
}
