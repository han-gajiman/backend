package com.hangajiman.ai.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.ai.domain.AiSuggestionLog;

public interface AiSuggestionLogRepository extends JpaRepository<AiSuggestionLog, Long> {

    long countByUserIdAndKindAndCreatedAtGreaterThanEqual(Long userId, String kind, Instant from);
}
