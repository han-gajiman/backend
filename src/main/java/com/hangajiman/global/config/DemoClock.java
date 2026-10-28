package com.hangajiman.global.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 서비스 날짜 계산용 Clock. 평소에는 실제 시간과 같고, 시연용 시간 이동(DEMO-01)으로 앞당길 수 있습니다.
 * 이동한 시간은 메모리에만 있어서 서버를 다시 켜면 실제 시간으로 돌아옵니다.
 * 토큰 만료 판단은 이 시계가 아니라 실제 시계(systemClock)를 씁니다.
 */
public class DemoClock extends Clock {

    private final Clock base;
    private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

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

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.offset(base.withZone(zone), offset.get());
    }

    @Override
    public Instant instant() {
        return base.instant().plus(offset.get());
    }
}
