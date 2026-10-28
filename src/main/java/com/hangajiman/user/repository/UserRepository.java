package com.hangajiman.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // 소셜 로그인 시 기존 회원 찾기 (없으면 자동 가입)
    Optional<User> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    // 진행 중(ACTIVE/COMPLETED) 돌탑이 있는지. Wish 엔티티가 생기면 WishRepository로 옮겨도 됩니다.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM wishes WHERE user_id = :userId AND status IN ('ACTIVE', 'COMPLETED'))",
            nativeQuery = true)
    boolean existsOpenWish(@Param("userId") Long userId);
}
