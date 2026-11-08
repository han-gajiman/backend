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

    // actionGoals: 실천 목표 후보, spotId: 시드 명소 중 하나(없으면 null), sensitive: 위기·자해 등 추천하면 안 되는 소원
    record Suggestion(List<String> actionGoals, Long spotId, String reason, boolean sensitive) {
    }
}
