-- PLAN-01~03 소원 캘린더 (11/9)

-- 날짜별 할 일 (하루 5개, 40자). 돌 쌓을 때 기록에 넣으면 완료
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

-- 앞으로 할 소원 메모 (시작 예정일부터 30일, 1인 12개). 진행 중 돌탑·다른 메모의 30일과 겹치지 않게 서비스에서 확인
CREATE TABLE wish_plans (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content    VARCHAR(100) NOT NULL,
  start_date DATE         NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wish_plans_user ON wish_plans(user_id, start_date);

-- AI 호출 기록에 종류 추가: 소원 추천(WISH)과 할 일 추천(TODO)을 하루 10번씩 따로 셉니다
ALTER TABLE ai_suggestion_logs ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'WISH' CHECK (kind IN ('WISH','TODO'));
DROP INDEX IF EXISTS idx_ai_logs_user_day;
CREATE INDEX idx_ai_logs_user_kind_day ON ai_suggestion_logs(user_id, kind, created_at);
