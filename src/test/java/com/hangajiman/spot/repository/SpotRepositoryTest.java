package com.hangajiman.spot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.hangajiman.spot.domain.Spot;

// 실제 PostgreSQL(hangajiman_test)에 Flyway로 만든 테이블과 시드를 검사합니다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpotRepositoryTest {

    @Autowired
    private SpotRepository spotRepository;

    @Test
    void seededSpotsAreLoaded() {
        List<Spot> spots = spotRepository.findAll();

        assertThat(spots).extracting(Spot::getName)
                .containsExactlyInAnyOrder("팔공산 갓바위", "문경새재 과거길", "호미곶 해맞이광장");
        assertThat(spots).allSatisfy(spot -> {
            assertThat(spot.getRadiusM()).isEqualTo(500);
            assertThat(spot.getCreatedAt()).isNotNull();
        });
    }
}
