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
    // 자체 로그인: 없는 이메일과 틀린 비밀번호를 같은 에러로 답해 가입 여부가 드러나지 않게 합니다.
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    AUTH_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 쓰는 닉네임이에요. 다른 닉네임을 적어 주세요."),
    AUTH_RESET_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증코드가 맞지 않거나 만료됐어요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN, "온보딩을 먼저 완료해 주세요."),
    REQUIRED_AGREEMENT_MISSING(HttpStatus.BAD_REQUEST, "필수 약관에 동의해 주세요."),

    WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "돌탑을 찾을 수 없어요."),
    WISH_ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 진행 중인 돌탑이 있어요."),
    WISH_NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 돌탑이 아니에요."),
    WISH_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "진행 중인 돌탑이 아니에요."),
    TOWER_NOT_COMPLETED(HttpStatus.FORBIDDEN, "아직 30일이 지나지 않았어요."),
    STONE_ALREADY_TODAY(HttpStatus.CONFLICT, "오늘은 이미 돌을 쌓았어요."),

    SPOT_NOT_FOUND(HttpStatus.NOT_FOUND, "명소를 찾을 수 없어요."),
    POSTCARD_NOT_OWNED(HttpStatus.FORBIDDEN, "가진 엽서만 보낼 수 있어요."),
    LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "엽서를 찾을 수 없어요."),
    YEAR_REVIEW_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "1년이 지나고 돌탑이 있을 때 쓸 수 있어요."),
    YEAR_REVIEW_ALREADY(HttpStatus.CONFLICT, "이미 1년 회고를 남겼어요."),

    FRIEND_SELF_INVITE(HttpStatus.BAD_REQUEST, "본인의 초대는 수락할 수 없어요."),
    FRIEND_ALREADY(HttpStatus.CONFLICT, "이미 친구예요."),
    FRIEND_INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "잘못되었거나 만료된 초대예요."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "친구를 찾을 수 없어요."),
    CHEER_NOT_ALLOWED(HttpStatus.FORBIDDEN, "응원할 수 없는 돌탑이에요."),
    CHEER_TOO_LONG(HttpStatus.BAD_REQUEST, "응원은 100자까지 쓸 수 있어요."),
    NUDGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "지금은 콕 찌를 수 없는 친구예요."),
    NUDGE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘은 이미 콕 찔렀어요."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없어요."),
    AI_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 AI 추천 횟수를 모두 사용했어요."),
    PLAN_PAST_DATE(HttpStatus.BAD_REQUEST, "지난 날짜는 바꿀 수 없어요."),
    PLAN_TODO_LIMIT(HttpStatus.BAD_REQUEST, "하루에 5개까지 적을 수 있어요."),
    PLAN_TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없어요."),
    PLAN_WISH_LIMIT(HttpStatus.BAD_REQUEST, "소원 메모는 12개까지 적을 수 있어요."),
    PLAN_WISH_IN_TOWER(HttpStatus.BAD_REQUEST, "진행 중인 돌탑이 끝난 뒤 날짜에만 적을 수 있어요."),
    PLAN_WISH_OVERLAP(HttpStatus.BAD_REQUEST, "다른 소원 메모의 30일과 겹쳐요."),
    PLAN_WISH_NOT_FOUND(HttpStatus.NOT_FOUND, "소원 메모를 찾을 수 없어요."),
    PLAN_SUGGEST_OUT_OF_TOWER(HttpStatus.BAD_REQUEST, "진행 중인 돌탑 기간에만 추천받을 수 있어요.");

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
