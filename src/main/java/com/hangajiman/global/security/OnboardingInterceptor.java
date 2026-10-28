package com.hangajiman.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hangajiman.global.exception.BusinessException;
import com.hangajiman.global.exception.ErrorCode;
import com.hangajiman.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * AUTH-02: 온보딩을 마치지 않은 회원은 다른 기능을 쓸 수 없습니다. → 403 ONBOARDING_REQUIRED
 * 온보딩 전에도 필요한 경로는 WebConfig에서 제외합니다.
 * 토큰의 회원이 없으면(탈퇴 등) 401 AUTH_INVALID_TOKEN
 */
@Component
public class OnboardingInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public OnboardingInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return true; // 공개 API (명소 등)
        }

        boolean onboarded = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN))
                .isOnboarded();
        if (!onboarded) {
            throw new BusinessException(ErrorCode.ONBOARDING_REQUIRED);
        }
        return true;
    }
}
