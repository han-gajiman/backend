package com.hangajiman.auth.social;

/**
 * 소셜 로그인으로 확인한 회원. 회원번호(계정 고유 ID)만 씁니다.
 * 이메일·프로필 사진 등 다른 계정 정보는 쓰지 않아서 받지도 저장하지도 않습니다 (11/10, 개인정보 처리방침).
 *
 * @param providerUserId 카카오 회원번호 / Google sub
 */
public record SocialProfile(String providerUserId) {
}
