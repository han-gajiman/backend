package com.hangajiman.ai.domain;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * AI 추천 호출 기록 (ERD ai_suggestion_logs). 하루 10회 제한용이라 소원 글은 저장하지 않고 호출 시각만 남깁니다.
 */
@Entity
@Table(name = "ai_suggestion_logs")
public class AiSuggestionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    protected AiSuggestionLog() {
    }

    public AiSuggestionLog(Long userId) {
        this.userId = userId;
    }
}
