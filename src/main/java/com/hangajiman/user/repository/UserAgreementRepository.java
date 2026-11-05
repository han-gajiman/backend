package com.hangajiman.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hangajiman.user.domain.UserAgreement;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
}
