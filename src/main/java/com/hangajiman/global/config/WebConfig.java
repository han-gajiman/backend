package com.hangajiman.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hangajiman.global.security.OnboardingInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // 온보딩 전에도 호출할 수 있는 경로
    private static final String[] ONBOARDING_EXEMPT_PATHS = {
            "/api/v1/auth/**",               // 로그인, 재발급, 로그아웃
            "/api/v1/users/me",              // 내 정보 조회, 탈퇴
            "/api/v1/users/me/onboarding",
            "/api/v1/users/me/push-token",
            "/api/v1/dev/**",                // local 프로필 개발용
    };

    private final OnboardingInterceptor onboardingInterceptor;

    public WebConfig(OnboardingInterceptor onboardingInterceptor) {
        this.onboardingInterceptor = onboardingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboardingInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(ONBOARDING_EXEMPT_PATHS);
    }
}
