-- users 테이블에 이메일 및 비밀번호 컬럼 추가 (소셜 유저는 NULL 허용)
ALTER TABLE users ADD COLUMN IF EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF EXISTS password VARCHAR(255);

-- 이메일 중복 방지 인덱스
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email IS NOT NULL;