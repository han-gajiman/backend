-- 1년 회고 (11/5): 소원 묶음(회차, 첫 시작일부터 365일)이 지나면 한 번 쓰고 수정할 수 없습니다.
CREATE TABLE year_reviews (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  round      INT          NOT NULL,
  content    VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (user_id, round)
);
