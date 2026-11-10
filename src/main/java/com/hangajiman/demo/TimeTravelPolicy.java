package com.hangajiman.demo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hangajiman.user.domain.User;

/**
 * DEMO-01 시연용 시간 이동을 누가 쓸 수 있는지.
 * - demo.time-travel.enabled: local·demo 프로필에서만 true
 * - demo.time-travel.user-emails: 비어 있으면 서버 전체 이동(로컬 개발), 있으면 그 계정들만 이동(실서버, 11/9)
 *   목록의 계정들은 한 묶음이라, 누가 이동하든 묶음 전체의 날짜가 함께 움직입니다(11/10, 시연 계정 + 친구 계정)
 */
@Component
public class TimeTravelPolicy {

    private final boolean enabled;
    private final Set<String> allowedEmails;

    public TimeTravelPolicy(@Value("${demo.time-travel.enabled:false}") boolean enabled,
                            @Value("${demo.time-travel.user-emails:}") String emails) {
        this.enabled = enabled;
        this.allowedEmails = Arrays.stream(emails.split(","))
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> allowedEmails() {
        return allowedEmails;
    }

    // 계정별 이동인지 (허용 계정 목록이 있으면)
    public boolean perUser() {
        return !allowedEmails.isEmpty();
    }

    public boolean canTravel(User user) {
        if (!enabled) {
            return false;
        }
        return !perUser() || (user.getEmail() != null && allowedEmails.contains(user.getEmail().toLowerCase(Locale.ROOT)));
    }
}
