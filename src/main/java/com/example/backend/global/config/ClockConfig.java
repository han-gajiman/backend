package com.example.backend.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 모든 날짜·시간 계산은 이 Clock을 주입받아 사용합니다. (LocalDate.now() 직접 호출 금지)
 * 시연용 시간 가속(DEMO-01)은 이 Clock을 교체하는 방식으로 구현합니다.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
