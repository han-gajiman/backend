package com.hangajiman.user.domain;

import java.time.Instant;
import java.time.LocalDate;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

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

    // 최초 소셜 로그인 시 자동 가입 (AUTH-01)
    public static User create(SocialProvider provider, String providerUserId, String profileImageUrl) {
        return new User(provider, providerUserId, profileImageUrl);
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
