package com.hangajiman.stone.dto;

import java.time.LocalDate;
import java.util.List;

import com.hangajiman.stone.domain.Stone;
import com.hangajiman.stone.domain.StoneType;

import jakarta.validation.constraints.Size;

/**
 * 돌 API 요청·응답 (API 명세서 🪨 Stone)
 */
public final class StoneDtos {

    private StoneDtos() {
    }

    // POST /wishes/{wishId}/stones. content가 있으면 PRACTICE(실천 기록), 없으면 ATTENDANCE(출석)
    // doneTodoIds: 기록에 넣은 오늘 할 일 (PLAN-02, 쌓으면 완료). 내 것·오늘 것만 반영
    public record CreateStoneRequest(@Size(max = 100) String content, List<Long> doneTodoIds) {
    }

    public record CreateStoneResponse(Long stoneId, LocalDate stoneDate, StoneType type, long stoneCount) {
    }

    // GET /wishes/{wishId}/stones 항목
    public record StoneItem(Long stoneId, LocalDate stoneDate, StoneType type, String content) {

        public static StoneItem from(Stone stone) {
            return new StoneItem(stone.getId(), stone.getStoneDate(), stone.getType(), stone.getContent());
        }
    }
}
