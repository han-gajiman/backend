package com.hangajiman.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.auth.domain.RefreshToken;
import com.hangajiman.auth.dto.AuthDtos.LocalLoginRequest;
import com.hangajiman.auth.dto.AuthDtos.LocalSignUpRequest;
import com.hangajiman.auth.dto.AuthDtos.LoginResponse;
import com.hangajiman.auth.dto.AuthDtos.TokenResponse;
import com.hangajiman.auth.repository.RefreshTokenRepository;
import com.hangajiman.auth.social.GoogleClient;
import com.hangajiman.auth.social.KakaoClient;
import com.hangajiman.auth.social.SocialProfile;
import com.hangajiman.global.config.ClockConfig;
import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.global.security.JwtProvider;
import com.hangajiman.user.domain.SocialProvider;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.repository.UserRepository;

@Service
@Transactional
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KakaoClient kakaoClient;
    private final GoogleClient googleClient;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder; // 비밀번호 암호화 및 검증용
    private final Clock clock;
    private final Duration refreshTokenTtl;

    public AuthService(KakaoClient kakaoClient, GoogleClient googleClient, UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository, JwtProvider jwtProvider,
                       PasswordEncoder passwordEncoder,
                       @Qualifier(ClockConfig.SYSTEM_CLOCK) Clock clock, // 토큰 만료는 실제 시간 기준
                       @Value("${jwt.refresh-exp-days}") long refreshExpDays) {
        this.kakaoClient = kakaoClient;
        this.googleClient = googleClient;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.refreshTokenTtl = Duration.ofDays(refreshExpDays);
    }

    // AUTH-01 카카오 로그인 / 최초 로그인 시 자동 가입
    public LoginResponse loginWithKakao(String kakaoAccessToken) {
        return login(SocialProvider.KAKAO, kakaoClient.getProfile(kakaoAccessToken));
    }

    // AUTH-01 Google 로그인 / 최초 로그인 시 자동 가입
    public LoginResponse loginWithGoogle(String idToken) {
        return login(SocialProvider.GOOGLE, googleClient.verifyIdToken(idToken));
    }

    /**
     * 로컬 개발 전용 (DevAuthController, local 프로필). 카카오·Google SDK 없이 실제 로그인과 같은 토큰을 받습니다.
     * devKey가 같으면 같은 회원입니다. 카카오 회원번호 자리에 "dev-{devKey}"를 씁니다.
     */
    public LoginResponse loginForDev(String devKey) {
        return login(SocialProvider.KAKAO, new SocialProfile("dev-" + devKey, null));
    }

    private LoginResponse login(SocialProvider provider, SocialProfile profile) {
        Optional<User> existing = userRepository.findByProviderAndProviderUserId(provider, profile.providerUserId());
        User user = existing.orElseGet(() -> userRepository.save(
                User.create(provider, profile.providerUserId(), profile.profileImageUrl())));

        TokenResponse tokens = issueTokens(user.getId());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), existing.isEmpty(), user.isOnboarded());
    }

    // 자체 회원가입: 이메일은 소문자로 저장하고, 비밀번호는 BCrypt 해시만 저장합니다.
    // 닉네임을 가입할 때 받지만 약관 동의가 없으므로 onboarded=false → 프론트는 온보딩(약관 동의)으로 보냅니다.
    public LoginResponse signupLocal(LocalSignUpRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }

        String nickname = request.nickname().trim();
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        User user;
        try {
            user = userRepository.saveAndFlush(User.createLocal(email, passwordEncoder.encode(request.password()), nickname));
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 같은 이메일·닉네임으로 가입한 경우
            throw new BusinessException(userRepository.existsByEmail(email) ? ErrorCode.AUTH_EMAIL_DUPLICATED : ErrorCode.NICKNAME_DUPLICATED);
        }
        TokenResponse tokens = issueTokens(user.getId());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), true, user.isOnboarded());
    }

    // 자체 로그인: 없는 이메일·틀린 비밀번호 모두 AUTH_LOGIN_FAILED
    public LoginResponse loginLocal(LocalLoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_LOGIN_FAILED));

        TokenResponse tokens = issueTokens(user.getId());
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), false, user.isOnboarded());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }


    // AUTH-03 재발급: 기존 Refresh Token은 폐기하고 새로 발급(교체). 만료된 토큰도 지운 뒤 401
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN));
        refreshTokenRepository.delete(saved);
        if (saved.isExpired(clock.instant())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_REFRESH_TOKEN);
        }
        return issueTokens(saved.getUserId());
    }

    // AUTH-03 로그아웃: 본인의 해당 Refresh Token 폐기 (없어도 성공 처리)
    public void logout(Long userId, String refreshToken) {
        refreshTokenRepository.deleteByUserIdAndTokenHash(userId, hash(refreshToken));
    }

    /**
     * AUTH-06 회원 탈퇴 (즉시 삭제)
     * 1) 소셜 연결 끊기 2) Refresh Token 폐기 3) 알림 정리 4) 회원 삭제 → 나머지는 DB CASCADE
     * Google은 서버에 Google 액세스 토큰이 없어 연결 해제를 앱(revokeAccess)에서 합니다.
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));

        if (user.getProvider() == SocialProvider.KAKAO) {
            kakaoClient.unlink(user.getProviderUserId());
        }
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.deleteNotificationsOf(userId);
        userRepository.delete(user);
    }

    private TokenResponse issueTokens(Long userId) {
        String refreshToken = newRefreshToken();
        Instant expiresAt = clock.instant().plus(refreshTokenTtl);
        refreshTokenRepository.save(new RefreshToken(userId, hash(refreshToken), expiresAt));
        return new TokenResponse(jwtProvider.createAccessToken(userId), refreshToken);
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
