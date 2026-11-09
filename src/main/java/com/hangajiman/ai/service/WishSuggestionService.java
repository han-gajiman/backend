package com.hangajiman.ai.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.ai.client.WishSuggester;
import com.hangajiman.ai.client.WishSuggester.Suggestion;
import com.hangajiman.ai.domain.AiSuggestionLog;
import com.hangajiman.ai.dto.SuggestionDtos.SpotPick;
import com.hangajiman.ai.dto.SuggestionDtos.SuggestionResponse;
import com.hangajiman.ai.repository.AiSuggestionLogRepository;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.spot.domain.Spot;
import com.hangajiman.spot.repository.SpotRepository;

/**
 * WISH-02 AI 추천: 실천 목표 3개 + 명소 1곳. 대화형이 아닌 제공형 (11/7).
 * 1인 하루 10회(KST), AI가 실패하면 기본 문구, 위험한 소원이면 추천 대신 도움 안내.
 * AI 답은 그대로 믿지 않고 개수·길이·명소 id를 확인합니다.
 */
@Service
public class WishSuggestionService {

    public static final int DAILY_LIMIT = 10;
    private static final int GOAL_COUNT = 3;
    private static final int GOAL_MAX_LENGTH = 40;
    private static final int REASON_MAX_LENGTH = 80;

    static final List<String> DEFAULT_GOALS = List.of(
            "매일 10분 소원에 가까워지는 일 하기",
            "하루 한 줄 오늘의 실천 기록하기",
            "매일 같은 시간에 작은 목표 하나 끝내기");

    static final String SUPPORT_MESSAGE = """
            지금 많이 힘드신가요? 혼자 견디지 않아도 괜찮아요.
            자살예방상담전화 109, 정신건강위기상담 1577-0199에서 24시간 이야기를 들어 드려요.""";

    // AI 호출 전에 먼저 걸러내는 위기 표현 (AI도 sensitive로 한 번 더 판단)
    public static final List<String> CRISIS_WORDS = List.of("죽고 싶", "죽고싶", "자살", "자해", "극단적 선택", "사라지고 싶", "살기 싫");

    private final WishSuggester suggester;
    private final AiSuggestionLogRepository logRepository;
    private final SpotRepository spotRepository;
    private final Clock clock;

    public WishSuggestionService(WishSuggester suggester, AiSuggestionLogRepository logRepository,
                                 SpotRepository spotRepository, Clock clock) {
        this.suggester = suggester;
        this.logRepository = logRepository;
        this.spotRepository = spotRepository;
        this.clock = clock;
    }

    @Transactional
    public SuggestionResponse suggest(Long userId, String content) {
        Instant todayStart = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        if (logRepository.countByUserIdAndKindAndCreatedAtGreaterThanEqual(userId, AiSuggestionLog.WISH, todayStart) >= DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT);
        }
        logRepository.save(new AiSuggestionLog(userId, AiSuggestionLog.WISH));

        String wish = content.trim();
        if (CRISIS_WORDS.stream().anyMatch(wish::contains)) {
            return support();
        }
        List<Spot> spots = spotRepository.findAllByOrderByIdAsc();
        Optional<Suggestion> result = suggester.suggest(wish, spots);
        if (result.isPresent() && result.get().sensitive()) {
            return support();
        }
        return result.map(s -> toResponse(s, spots)).orElseGet(this::fallback);
    }

    private SuggestionResponse toResponse(Suggestion suggestion, List<Spot> spots) {
        List<String> goals = suggestion.actionGoals() == null ? List.of() : suggestion.actionGoals().stream()
                .filter(g -> g != null && !g.isBlank())
                .map(String::trim)
                .filter(g -> g.length() <= GOAL_MAX_LENGTH)
                .distinct()
                .limit(GOAL_COUNT)
                .toList();
        if (goals.size() < GOAL_COUNT) {
            return fallback();
        }
        Map<Long, Spot> byId = spots.stream().collect(Collectors.toMap(Spot::getId, Function.identity()));
        Spot spot = suggestion.spotId() == null ? null : byId.get(suggestion.spotId());
        String reason = suggestion.reason() == null ? "" : suggestion.reason().trim();
        SpotPick pick = spot == null || reason.isEmpty() || reason.length() > REASON_MAX_LENGTH
                ? null
                : new SpotPick(spot.getId(), spot.getName(), reason);
        return new SuggestionResponse(goals, pick, false, null);
    }

    private SuggestionResponse fallback() {
        return new SuggestionResponse(DEFAULT_GOALS, null, true, null);
    }

    private SuggestionResponse support() {
        return new SuggestionResponse(List.of(), null, false, SUPPORT_MESSAGE);
    }
}
