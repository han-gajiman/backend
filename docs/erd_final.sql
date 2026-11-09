-- ============================================================
-- 한 가지만 — ERD 수정본 (PostgreSQL)
-- 기준: Flyway 마이그레이션 V1~V8 (11/8 기준, 노션 ERD와 같은 내용)
-- 🟡 = 팀 결정 필요 / [변경] = 기존 ERD 대비 바뀐 부분 / [추가] = 새로 생긴 부분
-- 모든 테이블 created_at / updated_at (JPA Auditing, BaseEntity)
-- users 참조 FK는 ON DELETE CASCADE → 회원 탈퇴 시 즉시 삭제
-- ============================================================

-- 회원
CREATE TABLE users (
  id                 BIGSERIAL PRIMARY KEY,
  provider           VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO','GOOGLE','LOCAL')),  -- [변경 10/27] Apple 제외, Google 추가 (V3) / [변경 11/3] 자체 로그인 LOCAL (V5)
  provider_user_id   VARCHAR(100) NOT NULL,                -- 소셜 회원 고유 ID (LOCAL은 이메일)
  email              VARCHAR(255),                         -- [추가 11/3] 자체 로그인 이메일(소문자), 소셜 회원은 NULL. 부분 유니크 uq_users_email (V5)
  password           VARCHAR(255),                         -- [추가 11/3] BCrypt 해시, 소셜 회원은 NULL (V5)
  nickname           VARCHAR(30),                          -- 온보딩에서 설정
  profile_image_url  VARCHAR(500),
  push_token         VARCHAR(200),                         -- Expo push token (기기 1개 기준)
  onboarded_at       TIMESTAMPTZ,                          -- NULL이면 온보딩 미완료
  journey_started_on DATE,                                 -- 첫 돌탑 시작일 (1년 회차 기준점)
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_user_id)
);

-- [추가 11/3] 비밀번호 찾기 인증코드 (V6). 코드 원문 대신 BCrypt 해시, 10분 만료, 5번 틀리면 무효, 회원당 1개
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

-- [변경] 약관 동의 이력
-- 기존 UNIQUE(user_id, type)을 제거해서 변경할 때마다 행을 추가 (최신 행 = 현재 상태)
CREATE TABLE user_agreements (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type       VARCHAR(20) NOT NULL CHECK (type IN ('TERMS','PRIVACY')),  -- [변경 11/5] 위치 동의는 서버에 저장 안 함 (V7)
  version    VARCHAR(10) NOT NULL,                         -- 약관 버전
  agreed     BOOLEAN     NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agreements_latest ON user_agreements(user_id, type, created_at DESC);

-- Refresh Token (원문 대신 해시 저장, 재발급 시 교체)
CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(128) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ  NOT NULL,                        -- 발급 +14일
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- 명소 (시드: 갓바위 EARNEST / 문경새재 PASS / 호미곶 NEW_START)
CREATE TABLE spots (
  id                   BIGSERIAL PRIMARY KEY,
  name                 VARCHAR(50)   NOT NULL,
  region               VARCHAR(30)   NOT NULL,
  category             VARCHAR(20)   NOT NULL,             -- 🟡 카테고리 기준 (명소 1개가 여러 카테고리면 spot_categories 다대다로 분리)
  description          VARCHAR(300),
  story                VARCHAR(500),                       -- [추가 11/8] SPOT-05 명소 이야기 (V10)
  latitude             NUMERIC(10,7) NOT NULL,
  longitude            NUMERIC(10,7) NOT NULL,
  radius_m             INT           NOT NULL DEFAULT 500, -- 인증 반경
  background_image_url VARCHAR(500),                       -- 돌탑 배경 풍경 (지금은 앱 안 일러스트를 써서 비어 있음)
  postcard_image_url   VARCHAR(500),                       -- 특별 디지털 엽서
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- 소원 = 돌탑 1개 (30일). 카테고리는 spots.category에서 파생
-- status: ACTIVE    = 진행 중 (end_date 이전)
--         COMPLETED = 30일 경과, 회고 대기 (스케줄러가 전환)
--         CLOSED    = 회고 완료 (result/reflection 기록됨)
-- 🟡 중도 포기 상태는 없음 (소원 취소 불가 정책)
-- [변경] spot_visited_at 제거 → 배경은 spot_visits로 판단 (그 명소를 인증했으면 명소 일러스트, 아니면 기본 화면)
-- 🟡 소원 등록 전에 이미 방문한 명소를 고르면, 등록 시 기존 방문을 wish_id로 연결할지 결정
CREATE TABLE wishes (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  spot_id      BIGINT       NOT NULL REFERENCES spots(id),
  content      VARCHAR(100) NOT NULL,                      -- 소원 (수정 불가)
  action_goal  VARCHAR(100) NOT NULL,                      -- 실천 목표 (수정 불가)
  visibility   VARCHAR(20)  NOT NULL DEFAULT 'PRIVATE'     -- [변경] 기본값 FRIENDS → PRIVATE 🟡
               CHECK (visibility IN ('PRIVATE','FRIENDS')),
  status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE','COMPLETED','CLOSED')),
  start_date   DATE         NOT NULL,
  end_date     DATE         NOT NULL,                      -- start_date + 29
  round        INT          NOT NULL,                      -- 회차: (end_date - journey_started_on) / 365 + 1
  result       VARCHAR(20)  CHECK (result IN ('ACHIEVED','NOT_ACHIEVED')),
  reflection   VARCHAR(200),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CHECK (end_date = start_date + 29)                       -- [추가] 30일 보장
);
-- 진행 중(ACTIVE/COMPLETED) 돌탑은 1인 1개 🟡
-- (복수 허용으로 결정되면 이 인덱스 제거 + "오늘의 돌"/콕 찌르기 기준 재정의)
CREATE UNIQUE INDEX uq_wishes_one_open ON wishes(user_id)
  WHERE status IN ('ACTIVE','COMPLETED');
CREATE INDEX idx_wishes_user_round ON wishes(user_id, round);  -- 회차별 모아보기
CREATE INDEX idx_wishes_spot ON wishes(spot_id);

-- 돌 (하루 1개)
-- 돌 날짜가 start_date~end_date 안인지는 서비스 레이어에서 검증
CREATE TABLE stones (
  id         BIGSERIAL PRIMARY KEY,
  wish_id    BIGINT       NOT NULL REFERENCES wishes(id) ON DELETE CASCADE,
  stone_date DATE         NOT NULL,                        -- KST 기준
  type       VARCHAR(20)  NOT NULL CHECK (type IN ('ATTENDANCE','PRACTICE')),  -- 출석 / 실천 기록
  content    VARCHAR(100),                                 -- PRACTICE일 때 실천 기록
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (wish_id, stone_date),
  CHECK (type = 'ATTENDANCE' OR content IS NOT NULL)       -- [추가] 실천 기록은 내용 필수
);

-- 명소 방문 인증 (11/5부터 좌표·거리 저장 안 함, 앱이 기기 안에서 확인한 결과와 인증 시각만)
CREATE TABLE spot_visits (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  spot_id     BIGINT      NOT NULL REFERENCES spots(id),
  wish_id     BIGINT      REFERENCES wishes(id) ON DELETE CASCADE,  -- 진행 중 돌탑 명소면 연결
  verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE spot_visits ADD CONSTRAINT uq_spot_visits_user_spot UNIQUE (user_id, spot_id);  -- [변경 11/5] 회원당 명소 하나, 좌표·거리 컬럼 없음 (V7)
CREATE INDEX idx_spot_visits_wish ON spot_visits(wish_id);

-- 디지털 엽서 보관함
CREATE TABLE postcards (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  spot_id    BIGINT      NOT NULL REFERENCES spots(id),
  visit_id   BIGINT      NOT NULL REFERENCES spot_visits(id) ON DELETE CASCADE,
  issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, spot_id)                                -- 명소당 1장
);
CREATE INDEX idx_postcards_visit ON postcards(visit_id);

-- 친구 초대 (https 링크 코드)
-- 🟡 링크 1개를 7일간 여러 명이 쓸 수 있음. 유출 대비가 필요하면 max_uses 추가 검토
CREATE TABLE friend_invites (
  id          BIGSERIAL PRIMARY KEY,
  inviter_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invite_code VARCHAR(20) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,                        -- 발급 +7일
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_friend_invites_inviter ON friend_invites(inviter_id);

-- 친구 (수락 시 양방향 2행 저장, 같은 트랜잭션)
CREATE TABLE friendships (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, friend_id),
  CHECK (user_id <> friend_id)
);
CREATE INDEX idx_friendships_friend ON friendships(friend_id);  -- [추가] 탈퇴 CASCADE·역방향 조회용

-- 응원 (친구 또는 익명 소원지기). 작성자 탈퇴 시 같이 삭제
-- 작성자가 친구/소원지기인지는 서비스 레이어에서 검증
CREATE TABLE cheers (
  id           BIGSERIAL PRIMARY KEY,
  wish_id      BIGINT       NOT NULL REFERENCES wishes(id) ON DELETE CASCADE,
  writer_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message      VARCHAR(100) NOT NULL,                      -- 100자
  is_anonymous BOOLEAN      NOT NULL DEFAULT false,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_cheers_wish ON cheers(wish_id, created_at DESC);
CREATE INDEX idx_cheers_writer ON cheers(writer_id);

-- 익명 소원지기 (P2, 돌탑당 1명)
-- 🟡 지금 구조는 자동 매칭형(matched_at) 가정. 요청·수락형으로 정해지면 keeper_requests 추가
-- keeper_user_id <> wishes.user_id 는 서비스 레이어에서 검증
CREATE TABLE anonymous_keepers (
  id             BIGSERIAL PRIMARY KEY,
  wish_id        BIGINT      NOT NULL UNIQUE REFERENCES wishes(id) ON DELETE CASCADE,
  keeper_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  matched_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_keepers_user ON anonymous_keepers(keeper_user_id);

-- [추가 11/5] 1년 회고: 끝난 회차에 한 번 쓰고 수정 불가 (TOWER-04, V8)
CREATE TABLE year_reviews (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  round      INT          NOT NULL,
  content    VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  UNIQUE (user_id, round)
);

-- [변경] 알림 (앱 내 + 푸시 발송 기록)
-- actor_id 추가: 보낸 사람 (CHEER, NUDGE). 시스템 알림은 NULL
-- target_id 의미 (타입별 약속):
--   CHEER           → cheers.id
--   NUDGE           → wishes.id (받는 사람의 진행 중 돌탑)
--   TOWER_COMPLETED → wishes.id
--   POSTCARD        → postcards.id
--   YEAR_REVIEW     → 끝난 회차 번호 (wishes.round, year_reviews.round)
-- 콕 찌르기 하루 1회: 같은 (actor_id, user_id, NUDGE)가 오늘(KST) 있는지 확인
CREATE TABLE notifications (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  actor_id   BIGINT       REFERENCES users(id) ON DELETE CASCADE,
  type       VARCHAR(20)  NOT NULL
             CHECK (type IN ('CHEER','NUDGE','TOWER_COMPLETED','POSTCARD','YEAR_REVIEW')),
  target_id  BIGINT       NOT NULL,
  message    VARCHAR(200) NOT NULL,
  is_read    BOOLEAN      NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_noti_inbox ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_noti_nudge ON notifications(actor_id, user_id, created_at DESC)
  WHERE type = 'NUDGE';

-- [추가] AI 실천 목표 추천 호출 기록 (WISH-02: 1인 하루 10회 제한용)
-- 소원 내용은 저장하지 않고 호출 시각만 남김
CREATE TABLE ai_suggestion_logs (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind       VARCHAR(20) NOT NULL DEFAULT 'WISH' CHECK (kind IN ('WISH','TODO')),  -- [추가 11/9] 소원 추천·할 일 추천을 하루 10회씩 따로 (V12)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_logs_user_kind_day ON ai_suggestion_logs(user_id, kind, created_at);

-- [추가 11/9] PLAN-02 소원 캘린더 날짜별 할 일 (V12). 하루 5개, 지난 날짜는 바꿀 수 없음(서비스)
CREATE TABLE plan_todos (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  todo_date  DATE        NOT NULL,
  content    VARCHAR(40) NOT NULL,
  done       BOOLEAN     NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_plan_todos_user_date ON plan_todos(user_id, todo_date);

-- [추가 11/9] PLAN-01 앞으로 할 소원 메모 (V12). 시작 예정일부터 30일, 1인 12개, 진행 중 돌탑·다른 메모와 겹치지 않게(서비스)
CREATE TABLE wish_plans (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content    VARCHAR(100) NOT NULL,
  start_date DATE         NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wish_plans_user ON wish_plans(user_id, start_date);
