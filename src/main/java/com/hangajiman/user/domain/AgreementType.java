package com.hangajiman.user.domain;

/**
 * 동의 항목. 둘 다 필수입니다. 위치는 서버에 저장하지 않으므로 동의 항목이 없습니다 (기기 권한으로 처리, V7).
 */
public enum AgreementType {
    TERMS,
    PRIVACY
}
