package com.hangajiman.global.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 서비스 날짜 계산용 Clock. 평소에는 실제 시간과 같고, 시연용 시간 이동(DEMO-01)으로 앞당길 수 있습니다.
 * - 서버 전체 이동(offset): 로컬 개발용. 모든 회원의 날짜가 함께 움직입니다.
 * - 계정별 이동(userOffsets, 11/9): 실서버용. 요청을 보낸 회원(JwtAuthFilter가 bindUser)의 날짜만 움직이고,
 *   다른 회원과 스케줄러(요청 밖)는 실제 날짜 그대로입니다.
 * 이동한 시간은 메모리에만 있어서 서버를 다시 켜면 실제 시간으로 돌아옵니다.
 * 토큰 만료 판단은 이 시계가 아니라 실제 시계(systemClock)를 씁니다.
 */
public class DemoClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);
    private final Map<Long, Duration> userOffsets = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    public DemoClock(Clock base) {
        this.base = base;
    }

    public void travel(Duration amount) {
        offset.updateAndGet(current -> current.plus(amount));
    }

    public void reset() {
        offset.set(Duration.ZERO);
    }

    public Duration getOffset() {
        return offset.get();
    }

    // 이 요청을 보낸 회원 (요청이 끝나면 unbindUser)
    public void bindUser(Long userId) {
        CURRENT_USER.set(userId);
    }

    public void unbindUser() {
        CURRENT_USER.remove();
    }

    public void travelUser(Long userId, Duration amount) {
        userOffsets.merge(userId, amount, Duration::plus);
    }

    public void resetUser(Long userId) {
        userOffsets.remove(userId);
    }

    public Duration getUserOffset(Long userId) {
        return userOffsets.getOrDefault(userId, Duration.ZERO);
    }

    private Duration effectiveOffset() {
        Long userId = CURRENT_USER.get();
        return userId == null ? offset.get() : offset.get().plus(getUserOffset(userId));
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.offset(base.withZone(zone), effectiveOffset());
    }

    @Override
    public Instant instant() {
        return base.instant().plus(effectiveOffset());
    }
}
