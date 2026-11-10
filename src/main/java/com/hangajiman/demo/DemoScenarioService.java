package com.hangajiman.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.config.DemoClock;
import com.hangajiman.user.domain.User;

/**
 * DEMO-03 시연 계정을 처음 세팅한 상태로 되돌립니다 (11/10).
 * 시연 계정(demo@hangajiman.app)과 친구 계정(friend@hangajiman.app)의 기록을 지우고 오늘 기준으로 다시 만듭니다.
 * 내용은 resources/demo/seed-demo.sql (EC2에서 psql로 직접 실행해도 같은 결과). 시간 이동한 날짜도 실제 날짜로 돌립니다.
 */
@Service
public class DemoScenarioService {

    public static final String DEMO_EMAIL = "demo@hangajiman.app";
    private static final String SEED = "demo/seed-demo.sql";

    private final JdbcTemplate jdbcTemplate;
    private final DemoClock clock;
    private final TimeTravelPolicy policy;

    public DemoScenarioService(JdbcTemplate jdbcTemplate, DemoClock clock, TimeTravelPolicy policy) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.policy = policy;
    }

    // 내 정보에 "시연 처음 상태로" 버튼을 보여줄지: 시간 이동이 허용된 시연 계정 본인만
    public boolean canReset(User user) {
        return policy.canTravel(user) && DEMO_EMAIL.equalsIgnoreCase(user.getEmail());
    }

    @Transactional
    public void reset(User user) {
        clock.resetUser(user.getId());
        jdbcTemplate.execute(readSeed());
    }

    private String readSeed() {
        try {
            return new ClassPathResource(SEED).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("시연 시드 파일을 읽지 못했어요: " + SEED, e);
        }
    }
}
