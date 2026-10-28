package com.hangajiman.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 작업 실행 (TOWER-01 30일 완성 처리 등), @Async 작업 실행 (NOTI-01 푸시 발송)
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
