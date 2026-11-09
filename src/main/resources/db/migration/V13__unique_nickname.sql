-- AUTH-04 닉네임은 회원끼리 겹치지 않습니다 (11/9). 변경은 PATCH /users/me/nickname

-- 이미 겹친 닉네임이 있으면 먼저 가입한 회원은 그대로 두고, 나중 회원은 닉네임 뒤에 _회원번호를 붙입니다 (30자 안에서)
UPDATE users u
SET nickname = left(u.nickname, 30 - length(u.id::text) - 1) || '_' || u.id
WHERE u.nickname IS NOT NULL
  AND EXISTS (SELECT 1 FROM users o WHERE o.nickname = u.nickname AND o.id < u.id);

CREATE UNIQUE INDEX uq_users_nickname ON users(nickname) WHERE nickname IS NOT NULL;
