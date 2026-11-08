-- SPOT-04 엽서 (11/8). 명소마다 엽서 4종(style 1~4), 인증할 때마다 아직 없는 1장을 무작위로 받습니다.

-- 같은 명소는 돌탑(진행 중 소원)마다 한 번 인증할 수 있습니다. wish_id = 인증할 때 진행 중이던 돌탑
ALTER TABLE spot_visits DROP CONSTRAINT uq_spot_visits_user_spot;
CREATE UNIQUE INDEX uq_spot_visits_tower ON spot_visits(user_id, spot_id, wish_id) WHERE wish_id IS NOT NULL;
CREATE UNIQUE INDEX uq_spot_visits_no_tower ON spot_visits(user_id, spot_id) WHERE wish_id IS NULL;

-- 엽서: 명소당 1장 → 명소당 스타일별 1장
ALTER TABLE postcards ADD COLUMN style SMALLINT NOT NULL DEFAULT 1 CHECK (style BETWEEN 1 AND 4);
ALTER TABLE postcards ALTER COLUMN style DROP DEFAULT;
ALTER TABLE postcards DROP CONSTRAINT postcards_user_id_spot_id_key;
ALTER TABLE postcards ADD CONSTRAINT uq_postcards_user_spot_style UNIQUE (user_id, spot_id, style);

-- 이미 인증한 명소에는 엽서 1장을 무작위로 지급
INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at)
SELECT user_id, spot_id, id, 1 + floor(random() * 4)::int, verified_at FROM spot_visits;

-- 엽서 보내기: 가진 엽서 뒷면에 글을 써서 친구 또는 나에게. 보낸 글은 고칠 수 없습니다.
CREATE TABLE postcard_letters (
  id          BIGSERIAL PRIMARY KEY,
  sender_id   BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  receiver_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  spot_id     BIGINT       NOT NULL REFERENCES spots(id),
  style       SMALLINT     NOT NULL CHECK (style BETWEEN 1 AND 4),
  message     VARCHAR(100) NOT NULL,
  read_at     TIMESTAMPTZ,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_postcard_letters_receiver ON postcard_letters(receiver_id, created_at DESC);
CREATE INDEX idx_postcard_letters_sender ON postcard_letters(sender_id);
