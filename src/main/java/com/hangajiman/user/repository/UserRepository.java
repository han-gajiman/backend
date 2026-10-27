package com.hangajiman.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // 소셜 로그인 시 기존 회원 찾기 (없으면 자동 가입)
    Optional<User> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);
}
