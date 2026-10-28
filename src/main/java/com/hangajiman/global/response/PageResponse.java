package com.hangajiman.global.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

/**
 * API 명세서 페이지네이션 응답 형식 (📄 표시 API 공통). 기본 size 20, 최대 50.
 */
public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    // ?page=0&size=20 → Pageable. 음수 page는 0, size는 1~50으로 맞춥니다.
    public static Pageable pageable(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), sort);
    }

    public static <E, T> PageResponse<T> of(Slice<E> slice, Function<E, T> mapper) {
        return new PageResponse<>(slice.map(mapper).getContent(), slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
