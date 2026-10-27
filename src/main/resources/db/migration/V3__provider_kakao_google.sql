-- 소셜 로그인: Apple 제외, 카카오 + Google로 결정 (10/27)
ALTER TABLE users DROP CONSTRAINT users_provider_check;
ALTER TABLE users ADD CONSTRAINT users_provider_check CHECK (provider IN ('KAKAO','GOOGLE'));
