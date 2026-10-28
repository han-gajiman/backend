package com.hangajiman.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndTokenHash(Long userId, String tokenHash);

    void deleteByUserId(Long userId);
}
