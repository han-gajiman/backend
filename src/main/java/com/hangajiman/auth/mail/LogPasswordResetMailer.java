package com.hangajiman.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

// 메일 서버 설정이 없을 때 (로컬 개발): 인증코드를 서버 로그에 찍습니다. 운영 배포에는 SPRING_MAIL_HOST를 꼭 설정하세요.
@Component
@ConditionalOnExpression("'${spring.mail.host:}'.isEmpty()")
public class LogPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LogPasswordResetMailer.class);

    @Override
    public void sendCode(String email, String code) {
        log.warn("[메일 설정 없음] 비밀번호 찾기 인증코드 {} → {}", code, email);
    }
}
