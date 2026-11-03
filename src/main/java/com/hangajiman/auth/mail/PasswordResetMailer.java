package com.hangajiman.auth.mail;

/**
 * 비밀번호 찾기 인증코드 메일. 메일 서버 설정(SPRING_MAIL_HOST)이 있으면 SMTP로 보내고, 없으면 서버 로그에 찍습니다.
 */
public interface PasswordResetMailer {

    void sendCode(String email, String code);
}
