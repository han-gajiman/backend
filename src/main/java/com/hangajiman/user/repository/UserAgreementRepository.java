package com.hangajiman.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.user.domain.AgreementType;
import com.hangajiman.user.domain.UserAgreement;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

    // 현재 동의 상태 = 같은 type의 가장 최근 행
    Optional<UserAgreement> findFirstByUserIdAndTypeOrderByIdDesc(Long userId, AgreementType type);
}
