package com.hangajiman.wish.dto;

import java.time.LocalDate;

import com.hangajiman.wish.domain.Visibility;
import com.hangajiman.wish.domain.WishResult;
import com.hangajiman.wish.domain.WishStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 소원 API 요청·응답 (API 명세서 🌠 Wish)
 */
public final class WishDtos {

    private WishDtos() {
    }

    // POST /wishes. visibility를 생략하면 PRIVATE 🟡
    public record CreateWishRequest(
            @NotBlank @Size(max = 100) String content,
            @NotBlank @Size(max = 100) String actionGoal,
            @NotNull Long spotId,
            Visibility visibility
    ) {
    }

    public record CreateWishResponse(Long wishId, LocalDate startDate, LocalDate endDate) {
    }

    // GET /wishes/current. 날짜 판단(stonedToday, dayIndex, daysLeft)은 모두 서버 Clock 기준
    public record CurrentWishResponse(
            Long wishId,
            String content,
            String actionGoal,
            WishStatus status,
            Visibility visibility,
            SpotSummary spot,
            long stoneCount,
            boolean stonedToday,
            int dayIndex,
            int daysLeft
    ) {
    }

    // visited=false → backgroundImageUrl null → 프론트는 허허벌판 기본 배경
    public record SpotSummary(Long id, String name, String category, boolean visited, String backgroundImageUrl) {
    }

    // POST /wishes/{wishId}/reflection. 한 줄 회고는 비워도 됩니다.
    public record ReflectionRequest(@NotNull WishResult result, @Size(max = 200) String reflection) {
    }

    // GET /wishes?round=1 항목 (회차별 완성 돌탑, 최대 12개)
    public record WishSummary(
            Long wishId,
            String content,
            LocalDate startDate,
            long stoneCount,
            String spotName,
            boolean visited,
            WishResult result // 회고 전(COMPLETED)이면 null
    ) {
    }
}
