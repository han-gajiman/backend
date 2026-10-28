package com.hangajiman.global.exception;

import org.springframework.http.HttpStatus;

/**
 * API 명세서 에러 코드 목록. INVALID_INPUT, NOT_FOUND, INTERNAL_ERROR는 명세서에 없는 공통 코드라 따로 추가했습니다.
 */
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않아요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요."),

    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    AUTH_SOCIAL_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했어요."),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 먼저 완료해 주세요."),
    REQUIRED_AGREEMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),
    LOCATION_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "위치정보 이용에 동의해 주세요."),

    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "돌탑을 찾을 수 없어요."),
    WISH_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 진행 중인 돌탑이 있어요."),
    WISH_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 돌탑이 아니에요."),
    WISH_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "진행 중인 돌탑이 아니에요."),
    TOWER_NOT_COMPLETED(HttpStatus.FORBIDDEN, "아직 30일이 지나지 않았어요."),
    STONE_ALREADY_TODAY(HttpStatus.CONFLICT, "오늘은 이미 돌을 쌓았어요."),

    SPOT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "명소 반경 밖이에요."),
    SPOT_ALREADY_VISITED_TODAY(HttpStatus.CONFLICT, "오늘 이미 인증한 명소예요."),

    FRIEND_SELF_INVITE(HttpStatus.BAD_REQUEST, "본인의 초대는 수락할 수 없어요."),
    FRIEND_ALREADY(HttpStatus.CONFLICT, "이미 친구예요."),
    FRIEND_INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "잘못되었거나 만료된 초대예요."),
    CHEER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "응원할 수 없는 돌탑이에요."),
    CHEER_TOO_LONG(HttpStatus.BAD_REQUEST, "응원은 100자까지 쓸 수 있어요."),
    NUDGE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 이미 콕 찔렀어요."),
    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 추천 횟수를 모두 사용했어요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
