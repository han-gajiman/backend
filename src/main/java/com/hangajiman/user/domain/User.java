package com.hangajiman.user.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.hangajiman.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원 (ERD users). 최초 소셜 로그인 시 create()로 가입되고, 온보딩 전에는 nickname·onboardedAt이 null입니다.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public static final int DAYS_PER_ROUND = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    // 자체 로그인용 이메일(소문자)과 BCrypt 비밀번호 해시 (소셜 회원은 null, V5)
    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(length = 30)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "push_token", length = 200)
    private String pushToken;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    // 첫 돌탑 시작일 (1년 회차 기준점)
    @Column(name = "journey_started_on")
    private LocalDate journeyStartedOn;

    protected User() {
    }

    private User(SocialProvider provider, String providerUserId, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.profileImageUrl = profileImageUrl;
    }

    // 자체 로그인용 생성자
    private User(String email, String password, String nickname) {
        this.provider = SocialProvider.LOCAL;
        this.providerUserId = email; // 자체 로그인은 이메일을 providerUserId로 사용 (UNIQUE(provider, provider_user_id))
        this.email = email;
        this.password = password;
        this.nickname = nickname; // 가입 화면에서 받음. 약관 동의는 온보딩에서
    }

    // 최초 소셜 로그인 시 자동 가입 (AUTH-01)
    public static User create(SocialProvider provider, String providerUserId, String profileImageUrl) {
        return new User(provider, providerUserId, profileImageUrl);
    }

    // 자체 회원가입 시 가입 메서드
    public static User createLocal(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname);
    }

    public boolean isOnboarded() {
        return onboardedAt != null;
    }

    // AUTH-02 온보딩 완료. now는 주입된 Clock 기준 시각을 넘기세요.
    public void completeOnboarding(String nickname, Instant now) {
        this.nickname = nickname;
        this.onboardedAt = now;
    }

    // 첫 돌탑을 시작할 때 한 번만 기록됩니다.
    public void startJourneyIfFirst(LocalDate startDate) {
        if (journeyStartedOn == null) {
            journeyStartedOn = startDate;
        }
    }

    /**
     * date가 속한 회차. 첫 돌탑 시작일부터 365일 = 1회차 (ERD wishes.round와 같은 공식).
     * 돌탑을 시작한 적이 없으면 1.
     */
    public int roundOf(LocalDate date) {
        if (journeyStartedOn == null) {
            return 1;
        }
        return (int) (ChronoUnit.DAYS.between(journeyStartedOn, date) / DAYS_PER_ROUND) + 1;
    }

    // round회차 회고 알림 날짜 = 시작일 + 365일 × round
    public LocalDate reviewDateOf(int round) {
        return journeyStartedOn == null ? null : journeyStartedOn.plusDays((long) DAYS_PER_ROUND * round);
    }

    public void updatePushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    public Long getId() {
        return id;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getPushToken() {
        return pushToken;
    }

    public Instant getOnboardedAt() {
        return onboardedAt;
    }

    public LocalDate getJourneyStartedOn() {
        return journeyStartedOn;
    }
}
