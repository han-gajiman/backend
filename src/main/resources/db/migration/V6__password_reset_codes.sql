-- 비밀번호 찾기 인증코드 (11/3). 코드 원문은 저장하지 않고 BCrypt 해시만 저장
-- 만료 10분, 5번 틀리면 무효, 새 코드를 보내면 이전 코드는 삭제
CREATE TABLE password_reset_codes (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash   VARCHAR(100) NOT NULL,
  expires_at  TIMESTAMPTZ  NOT NULL,
  attempts    INT          NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_codes_user ON password_reset_codes(user_id);
