package com.hangajiman.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.auth.domain.PasswordResetCode;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    // 회원당 코드는 하나만 유지 (새로 보내면 이전 코드 삭제)
    Optional<PasswordResetCode> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
