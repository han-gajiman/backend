package com.hangajiman.user.service;

import java.time.Clock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.domain.AgreementType;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.domain.UserAgreement;
import com.hangajiman.user.dto.MeResponse;
import com.hangajiman.postcard.repository.PostcardLetterRepository;
import com.hangajiman.user.dto.OnboardingRequest;
import com.hangajiman.user.repository.UserAgreementRepository;
import com.hangajiman.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    // 위치 동의만 바꿀 때 이전 기록이 없으면 쓰는 약관 버전

    private final UserRepository userRepository;
    private final UserAgreementRepository agreementRepository;
    private final PostcardLetterRepository letterRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, UserAgreementRepository agreementRepository,
                       PostcardLetterRepository letterRepository, Clock clock) {
        this.userRepository = userRepository;
        this.agreementRepository = agreementRepository;
        this.letterRepository = letterRepository;
        this.clock = clock;
    }

    // 토큰의 userId로 회원 조회. 탈퇴 등으로 없으면 401
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN));
    }

    // AUTH-02 온보딩: 필수 약관 확인 → 동의 이력 저장 → 닉네임 설정
    @Transactional
    public void onboard(Long userId, OnboardingRequest request) {
        OnboardingRequest.Agreements agreements = request.agreements();
        if (!agreements.terms() || !agreements.privacy()) {
            throw new BusinessException(ErrorCode.REQUIRED_AGREEMENT_MISSING);
        }

        User user = getUser(userId);
        String version = request.agreementVersion();
        agreementRepository.save(new UserAgreement(userId, AgreementType.TERMS, version, true));
        agreementRepository.save(new UserAgreement(userId, AgreementType.PRIVACY, version, true));

        String nickname = requireAvailableNickname(request.nickname(), userId);
        user.completeOnboarding(nickname, clock.instant());
        flushNickname();
    }

    // AUTH-04 PATCH /users/me/nickname — 회원끼리 겹치면 409 NICKNAME_DUPLICATED
    @Transactional
    public void changeNickname(Long userId, String requested) {
        User user = getUser(userId);
        user.changeNickname(requireAvailableNickname(requested, userId));
        flushNickname();
    }

    private String requireAvailableNickname(String requested, Long userId) {
        String nickname = requested.trim();
        if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        return nickname;
    }

    // 거의 동시에 같은 닉네임을 정한 경우 DB 유니크 인덱스(uq_users_nickname)에서 걸림
    private void flushNickname() {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
    }

    // AUTH-04 내 정보
    public MeResponse getMe(Long userId) {
        User user = getUser(userId);
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                userRepository.existsOpenWish(userId),
                letterRepository.existsByReceiverIdAndReadAtIsNull(userId)
        );
    }

    // AUTH-04 Expo 푸시 토큰 등록 (기기 1개 기준, 덮어쓰기)
    @Transactional
    public void updatePushToken(Long userId, String pushToken) {
        getUser(userId).updatePushToken(pushToken);
    }
}
