package com.hangajiman.ai.client;

import java.util.List;
import java.util.Optional;

import com.hangajiman.spot.domain.Spot;

/**
 * WISH-02 LLM 호출. 소원 글만 보내고 회원 정보는 보내지 않습니다.
 * 실패·시간 초과·거절이면 Optional.empty() (서비스가 기본 문구로 대체)
 */
public interface WishSuggester {

    Optional<Suggestion> suggest(String wish, List<Spot> spots);

    /**
     * PLAN-03 소원 캘린더 할 일 1개. dayIndex는 돌탑의 몇째 날(1~30), existingTodos는 그날 이미 적은 할 일(겹치지 않게),
     * angle은 이번 추천의 방향(다시 추천할 때마다 바꿔서 비슷한 문장이 반복되지 않게). 실패하면 Optional.empty()
     */
    default Optional<String> suggestTodo(String wish, String actionGoal, int dayIndex, List<String> existingTodos,
                                         String angle) {
        return Optional.empty();
    }

    // actionGoals: 실천 목표 후보, spotId: 시드 명소 중 하나(없으면 null), sensitive: 위기·자해 등 추천하면 안 되는 소원
    record Suggestion(List<String> actionGoals, Long spotId, String reason, boolean sensitive) {
    }
}
