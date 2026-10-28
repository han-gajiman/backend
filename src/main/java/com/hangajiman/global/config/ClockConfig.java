package com.hangajiman.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 모든 날짜·시간 계산은 Clock을 주입받아 사용합니다. (LocalDate.now() 직접 호출 금지)
 * - clock (기본): 서비스 날짜(소원·돌·완성). 시연용 시간 이동(DEMO-01)으로 움직입니다.
 * - systemClock: 토큰 만료 판단. 시간 이동과 상관없이 실제 시간입니다. (이동하면 토큰이 모두 만료되어 시연이 끊기므로)
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final String SYSTEM_CLOCK = "systemClock";

    @Bean
    @Primary
    public DemoClock clock() {
        return new DemoClock(Clock.system(KST));
    }

    @Bean(SYSTEM_CLOCK)
    public Clock systemClock() {
        return Clock.system(KST);
    }
}
