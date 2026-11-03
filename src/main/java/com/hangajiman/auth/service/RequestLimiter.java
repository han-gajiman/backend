package com.hangajiman.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;

/**
 * 로그인 전 API(아이디 찾기, 인증코드 보내기)를 짧은 시간에 여러 번 부르지 못하게 막는 간단한 메모리 제한.
 * 서버 한 대 기준이며, 재시작하면 초기화됩니다. 시간은 실제 시간 기준(시연용 시간 이동과 무관).
 */
@Component
public class RequestLimiter {

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RequestLimiter(@Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock) {
        this.clock = clock;
    }

    // key별로 window 동안 max번까지 허용. 넘으면 429 TOO_MANY_REQUESTS
    public void check(String key, int max, Duration window) {
        Instant now = clock.instant();
        Window updated = windows.compute(key, (k, w) ->
                w == null || !w.start().plus(window).isAfter(now) ? new Window(now, 1) : new Window(w.start(), w.count() + 1));
        if (updated.count() > max) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
