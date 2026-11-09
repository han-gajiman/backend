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
 * kind: WISH(소원 등록 추천), TODO(소원 캘린더 할 일 추천) — 종류마다 하루 10회 (V12)
 */
@Entity
@Table(name = "ai_suggestion_logs")
public class AiSuggestionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static final String WISH = "WISH";
    public static final String TODO = "TODO";

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String kind;

    protected AiSuggestionLog() {
    }

    public AiSuggestionLog(Long userId, String kind) {
        this.userId = userId;
        this.kind = kind;
    }
}
