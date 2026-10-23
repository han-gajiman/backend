package com.hangajiman.global.response;

import java.util.List;

/**
 * API 명세서 페이지네이션 응답 형식 (📄 표시 API 공통). 기본 size 20, 최대 50.
 */
public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
}
