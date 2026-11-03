-- 자체 로그인(이메일·비밀번호) 추가 (11/3). 소셜 회원은 email·password가 NULL
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);  -- BCrypt 해시 (평문 저장 금지)

-- 이메일 중복 방지 (소문자로 저장)
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email ON users(email) WHERE email IS NOT NULL;

-- 가입 경로에 LOCAL 추가 (V3: KAKAO, GOOGLE)
ALTER TABLE users DROP CONSTRAINT users_provider_check;
ALTER TABLE users ADD CONSTRAINT users_provider_check CHECK (provider IN ('KAKAO','GOOGLE','LOCAL'));
