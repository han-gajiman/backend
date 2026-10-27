package com.hangajiman.user.domain;

/**
 * 소셜 로그인 제공자 (10/27 결정: 카카오 + Google, Apple 제외). DB users.provider 제약과 같습니다.
 */
public enum SocialProvider {
    KAKAO,
    GOOGLE
}
