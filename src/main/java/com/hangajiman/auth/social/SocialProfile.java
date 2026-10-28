package com.hangajiman.auth.social;

/**
 * 소셜 서비스에서 확인한 회원 정보
 *
 * @param providerUserId 카카오 회원번호 / Google sub
 * @param profileImageUrl 없으면 null
 */
public record SocialProfile(String providerUserId, String profileImageUrl) {
}
