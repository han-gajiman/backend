package com.hangajiman.auth.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// SPRING_MAIL_HOST 등 메일 서버 설정이 있을 때 (예: Gmail SMTP + 앱 비밀번호)
@Component
@ConditionalOnExpression("!'${spring.mail.host:}'.isEmpty()")
public class SmtpPasswordResetMailer implements PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpPasswordResetMailer(JavaMailSender mailSender, @Value("${mail.from:${spring.mail.username:}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("[한 가지만] 비밀번호 찾기 인증코드");
        message.setText("""
                인증코드: %s

                앱의 비밀번호 찾기 화면에 10분 안에 입력해 주세요.
                요청하지 않았다면 이 메일은 무시해 주세요.
                """.formatted(code));
        mailSender.send(message);
    }
}
