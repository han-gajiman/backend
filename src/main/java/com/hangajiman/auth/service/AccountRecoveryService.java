package com.hangajiman.auth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.auth.domain.PasswordResetCode;
import com.hangajiman.auth.mail.PasswordResetMailer;
import com.hangajiman.auth.repository.PasswordResetCodeRepository;
import com.hangajiman.auth.repository.RefreshTokenRepository;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

/**
 * 자체 로그인 계정 찾기: 아이디(이메일) 찾기, 비밀번호 찾기(이메일 인증코드 → 새 비밀번호).
 * 가입 여부가 드러나지 않도록, 없는 이메일에도 성공처럼 응답하고 코드 확인 실패는 한 가지 에러로 답합니다.
 */
@Service
@Transactional
public class AccountRecoveryService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_EMAILS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;

    public AccountRecoveryService(UserRepository userRepository, PasswordResetCodeRepository codeRepository,
                                  RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                                  PasswordResetMailer mailer,
                                  @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock) { // 코드 만료는 실제 시간 기준
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.clock = clock;
    }

    // 아이디 찾기: 닉네임으로 가입한 이메일을 가려서 돌려줍니다. (예: st***@ex***.com)
    @Transactional(readOnly = true)
    public List<String> findEmails(String nickname) {
        return userRepository.findByProviderAndNicknameOrderByIdAsc(SocialProvider.LOCAL, nickname.trim()).stream()
                .limit(MAX_EMAILS)
                .map(user -> mask(user.getEmail()))
                .toList();
    }

    // 인증코드 보내기: 이메일 회원이면 6자리 코드를 메일로 보냅니다. 없는 이메일이거나 60초 안에 다시 요청하면 조용히 넘어갑니다.
    public void sendResetCode(String rawEmail) {
        Optional<User> user = userRepository.findByEmail(normalize(rawEmail));
        if (user.isEmpty()) {
            return;
        }
        Long userId = user.get().getId();
        Instant now = clock.instant();
        Optional<PasswordResetCode> previous = codeRepository.findByUserId(userId);
        if (previous.isPresent() && previous.get().getExpiresAt().minus(CODE_TTL).plus(RESEND_INTERVAL).isAfter(now)) {
            return;
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codeRepository.deleteByUserId(userId);
        codeRepository.flush();
        codeRepository.save(new PasswordResetCode(userId, passwordEncoder.encode(code), now.plus(CODE_TTL)));
        mailer.sendCode(user.get().getEmail(), code);
    }

    // 새 비밀번호로 바꾸기: 코드가 맞으면 비밀번호를 바꾸고, 코드와 모든 기기의 로그인(Refresh Token)을 지웁니다.
    // 틀리면 틀린 횟수를 남겨야 하므로 롤백하지 않습니다.
    @Transactional(noRollbackFor = BusinessException.class)
    public void resetPassword(String rawEmail, String code, String newPassword) {
        User user = userRepository.findByEmail(normalize(rawEmail))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_CODE_INVALID));
        PasswordResetCode saved = codeRepository.findByUserId(user.getId())
                .filter(c -> c.isUsable(clock.instant()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_CODE_INVALID));

        if (!passwordEncoder.matches(code, saved.getCodeHash())) {
            saved.recordFailedAttempt();
            throw new BusinessException(ErrorCode.AUTH_RESET_CODE_INVALID);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        codeRepository.delete(saved);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // stone@example.com → st***@ex***.com
    static String mask(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String host = dot > 0 ? domain.substring(0, dot) : domain;
        String tld = dot > 0 ? domain.substring(dot) : "";
        return keep(local) + "***@" + keep(host) + "***" + tld;
    }

    private static String keep(String part) {
        return part.substring(0, Math.min(part.length() > 2 ? 2 : 1, part.length()));
    }
}
