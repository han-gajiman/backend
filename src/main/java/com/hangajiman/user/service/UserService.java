package com.hangajiman.user.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.domain.AgreementType;
import com.hangajiman.user.domain.User;
import com.hangajiman.user.domain.UserAgreement;
import com.hangajiman.user.dto.MeResponse;
import com.hangajiman.user.dto.OnboardingRequest;
import com.hangajiman.user.repository.UserAgreementRepository;
import com.hangajiman.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

    // 위치 동의만 바꿀 때 이전 기록이 없으면 쓰는 약관 버전
    private static final String DEFAULT_AGREEMENT_VERSION = "1.0";

    private final UserRepository userRepository;
    private final UserAgreementRepository agreementRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, UserAgreementRepository agreementRepository, Clock clock) {
        this.userRepository = userRepository;
        this.agreementRepository = agreementRepository;
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
        agreementRepository.save(new UserAgreement(userId, AgreementType.LOCATION, version, agreements.location()));

        user.completeOnboarding(request.nickname().trim(), clock.instant());
    }

    // AUTH-04 내 정보
    public MeResponse getMe(Long userId) {
        User user = getUser(userId);
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                new MeResponse.Agreements(hasAgreed(userId, AgreementType.LOCATION)),
                userRepository.existsOpenWish(userId),
                journeyOf(user)
        );
    }

    // AUTH-04 Expo 푸시 토큰 등록 (기기 1개 기준, 덮어쓰기)
    @Transactional
    public void updatePushToken(Long userId, String pushToken) {
        getUser(userId).updatePushToken(pushToken);
    }

    // AUTH-05 위치정보 동의 변경: 이력에 행을 추가합니다.
    @Transactional
    public void updateLocationAgreement(Long userId, boolean agreed) {
        getUser(userId);
        String version = agreementRepository.findFirstByUserIdAndTypeOrderByIdDesc(userId, AgreementType.LOCATION)
                .map(UserAgreement::getVersion)
                .orElse(DEFAULT_AGREEMENT_VERSION);
        agreementRepository.save(new UserAgreement(userId, AgreementType.LOCATION, version, agreed));
    }

    // 현재 동의 상태 (가장 최근 행). 기록이 없으면 미동의
    public boolean hasAgreed(Long userId, AgreementType type) {
        return agreementRepository.findFirstByUserIdAndTypeOrderByIdDesc(userId, type)
                .map(UserAgreement::isAgreed)
                .orElse(false);
    }

    private MeResponse.Journey journeyOf(User user) {
        if (user.getJourneyStartedOn() == null) {
            return null;
        }
        int currentRound = user.roundOf(LocalDate.now(clock));
        return new MeResponse.Journey(user.getJourneyStartedOn(), currentRound, user.reviewDateOf(currentRound));
    }
}
