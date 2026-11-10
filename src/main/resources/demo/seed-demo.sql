-- 시연용 계정(demo@hangajiman.app)과 친구 계정(friend@hangajiman.app)에 기록을 채웁니다.
-- 두 계정의 회원가입·온보딩·친구 맺기는 API로 먼저 해 둡니다.
-- 실행: 앱의 내 정보 > "시연 처음 상태로" (POST /demo/reset-scenario, 시연 계정만)
--   또는 cd ~/backend/deploy && docker compose exec -T db psql -U hangajiman -d hangajiman < seed-demo.sql
-- 다시 실행하면 두 계정의 돌탑·방문·엽서·주고받은 엽서·캘린더·응원을 지우고 오늘 기준으로 새로 만듭니다 (다른 회원은 그대로).
--
-- 만들어지는 상태 (오늘 기준):
--   시연 계정 소원지기
--     첫 번째 소원: 돌탑 12개(1년 다 채움), 1년 회고 작성 완료
--     두 번째 소원: 돌탑 6개, 1년이 지나 1년 회고를 쓸 수 있음
--     세 번째 소원(진행 중): 백엔드 개발자로 취업하기, 13일째, 돌 12개, 오늘은 아직 안 쌓음, 문경새재 과거길 방문함, 친구 공개
--     명소 방문 5곳(문경새재는 두 번), 엽서 14장(문경새재 4장 모두), 친구와 주고받은 엽서 4장 + 나에게 보낸 엽서 1장(안 읽은 받은 엽서 1장)
--     소원 캘린더 할 일·소원 메모, 돌돌이의 응원 2개
--   친구 계정 돌돌이: 매일 30분 달리기, 9일째, 돌 9개, 오늘은 아직 안 쌓음(콕 찌르기 가능), 불국사·석굴암 방문함

DO $$
DECLARE
  demo_id   BIGINT;
  friend_id BIGINT;
  d         DATE := (now() AT TIME ZONE 'Asia/Seoul')::date;
  j         DATE := (now() AT TIME ZONE 'Asia/Seoul')::date - 760;  -- 첫 돌탑 시작일 (2년 + 30일 전)
  w         BIGINT;
  cur       BIGINT;
  fw        BIGINT;
  s         DATE;
  n         INT;
  i         INT;
  k         INT;
  v         BIGINT;
  l         BIGINT;
  -- 방문 인증한 지난 돌탑(k)과 그때 받은 엽서 스타일
  visit_k   INT[] := ARRAY[0, 1, 2, 4, 13];
  wishes_txt TEXT[] := ARRAY['토익 850점 넘기','매일 아침 운동하기','책 12권 읽기','자격증 따기','일찍 일어나기',
                             '영어 회화 늘리기','블로그 꾸준히 쓰기','다이어트 5kg','코딩 테스트 통과하기','부모님께 매주 전화하기',
                             '포트폴리오 완성하기','기타 한 곡 완주하기','물 2L 마시기','하루 만 보 걷기',
                             '한국사 1급 따기','요리 10가지 배우기','명상 습관 만들기','중국어 기초 떼기'];
  goals_txt TEXT[] := ARRAY['매일 단어 30개 외우기','아침 20분 스트레칭','매일 20쪽 읽기','매일 기출 10문제','6시 30분 기상',
                            '매일 10분 쉐도잉','이틀에 한 편 쓰기','저녁 7시 이후 금식','매일 알고리즘 1문제','일요일 저녁 전화',
                            '매일 커밋 1개','매일 30분 연습','하루 물 8잔','점심 후 산책',
                            '매일 강의 1개','주 3회 요리','자기 전 10분 명상','매일 회화 5문장'];
  notes_txt TEXT[] := ARRAY['오늘도 해냈다.','귀찮았지만 끝까지 했다.','아침에 바로 끝내니 마음이 편하다.','생각보다 재밌었다.',
                            '내일은 더 일찍 해야지.','조금씩 익숙해지는 느낌이다.'];
  refl_txt  TEXT[] := ARRAY['매일 쌓으니 끝까지 해냈다','중간에 쉬었지만 다시 돌아왔다','습관이 된 것 같아 뿌듯하다',
                            '생각보다 어려웠지만 배운 게 많다'];
BEGIN
  SELECT id INTO demo_id FROM users WHERE email = 'demo@hangajiman.app';
  SELECT id INTO friend_id FROM users WHERE email = 'friend@hangajiman.app';
  IF demo_id IS NULL OR friend_id IS NULL THEN
    RAISE EXCEPTION '시연 계정(demo@hangajiman.app)과 친구 계정(friend@hangajiman.app)을 먼저 가입해 주세요.';
  END IF;

  -- 두 계정의 기록만 비우기 (돌·방문에 딸린 엽서·응원은 돌탑·방문 삭제로 함께 지워짐)
  DELETE FROM postcard_letters WHERE sender_id IN (demo_id, friend_id) OR receiver_id IN (demo_id, friend_id);
  DELETE FROM notifications WHERE user_id IN (demo_id, friend_id) AND type IN ('POSTCARD', 'CHEER', 'YEAR_REVIEW');
  DELETE FROM postcards WHERE user_id IN (demo_id, friend_id);
  DELETE FROM spot_visits WHERE user_id IN (demo_id, friend_id);
  DELETE FROM wishes WHERE user_id IN (demo_id, friend_id);
  DELETE FROM plan_todos WHERE user_id IN (demo_id, friend_id);
  DELETE FROM wish_plans WHERE user_id IN (demo_id, friend_id);
  DELETE FROM year_reviews WHERE user_id IN (demo_id, friend_id);

  UPDATE users SET journey_started_on = j WHERE id = demo_id;
  UPDATE users SET journey_started_on = d - 9 WHERE id = friend_id;

  -- 지난 돌탑 18개 (첫 번째 소원 12개: 30일 간격으로 1년을 다 채움 / 두 번째 소원 6개: 1년 + 10일부터 30일 간격)
  FOR k IN 0..17 LOOP
    s := CASE WHEN k < 12 THEN j + 30 * k ELSE j + 375 + 30 * (k - 12) END;
    INSERT INTO wishes (user_id, spot_id, content, action_goal, visibility, status, start_date, end_date, round, result, reflection)
    VALUES (demo_id, (k % 10) + 1, wishes_txt[k + 1], goals_txt[k + 1], 'FRIENDS', 'CLOSED', s, s + 29,
            ((s + 29) - j) / 365 + 1,
            CASE WHEN k % 3 = 2 THEN 'NOT_ACHIEVED' ELSE 'ACHIEVED' END,
            refl_txt[(k % 4) + 1])
    RETURNING id INTO w;

    n := 30 - (k % 4) * 3;  -- 30, 27, 24, 21개
    FOR i IN 0..29 LOOP
      IF (i * 7 + k) % 30 < n THEN
        INSERT INTO stones (wish_id, stone_date, type, content)
        VALUES (w, s + i,
                CASE WHEN i % 4 = 3 THEN 'ATTENDANCE' ELSE 'PRACTICE' END,
                CASE WHEN i % 4 = 3 THEN NULL WHEN i % 8 = 0 THEN goals_txt[k + 1] || ' 완료!' ELSE notes_txt[(i % 6) + 1] END);
      END IF;
    END LOOP;

    -- 그 돌탑의 명소를 방문 인증하고 엽서를 받음 (갓바위 4장, 문경새재 2장, 호미곶 3장, 불국사 1장 / 두 번째 소원 때 동화사 2장)
    IF k = ANY (visit_k) THEN
      INSERT INTO spot_visits (user_id, spot_id, wish_id, verified_at)
      VALUES (demo_id, (k % 10) + 1, w, (s + 5)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '15 hours')
      RETURNING id INTO v;
      INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at)
      SELECT demo_id, (k % 10) + 1, v, st, (s + 5)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '15 hours'
      FROM unnest(CASE k WHEN 0 THEN ARRAY[1, 2, 3, 4] WHEN 1 THEN ARRAY[1, 3] WHEN 2 THEN ARRAY[1, 3, 4]
                         WHEN 4 THEN ARRAY[2] ELSE ARRAY[1, 4] END) AS st;
    END IF;
  END LOOP;

  -- 1년 회고: 첫 번째 소원은 작성 완료, 두 번째 소원은 쓸 수 있게 비워 둠
  INSERT INTO year_reviews (user_id, round, content)
  VALUES (demo_id, 1, '하루에 한 가지씩 쌓다 보니 끝까지 해내는 사람이 되었어요. 못 한 날도 다음 날 다시 시작하면 된다는 걸 배웠어요.');

  -- 진행 중 돌탑(세 번째 소원): 12일 전 시작, 돌 12개, 오늘은 아직 안 쌓음 (시연에서 쌓기), 문경새재 과거길
  INSERT INTO wishes (user_id, spot_id, content, action_goal, visibility, status, start_date, end_date, round)
  VALUES (demo_id, 2, '백엔드 개발자로 취업하기', '매일 영어 단어 20개 외우기', 'FRIENDS', 'ACTIVE', d - 12, d + 17,
          ((d + 17) - j) / 365 + 1)
  RETURNING id INTO cur;
  FOR i IN 0..11 LOOP
    INSERT INTO stones (wish_id, stone_date, type, content)
    VALUES (cur, d - 12 + i,
            CASE WHEN i % 3 = 2 THEN 'ATTENDANCE' ELSE 'PRACTICE' END,
            CASE WHEN i % 3 = 2 THEN NULL WHEN i % 2 = 0 THEN '영어 단어 20개 외움' ELSE notes_txt[(i % 6) + 1] END);
  END LOOP;
  -- 이번 돌탑에서 문경새재를 다시 인증 (지난 돌탑 때 받은 1·3번에 이어 2·4번 → 문경새재 4장 모두)
  INSERT INTO spot_visits (user_id, spot_id, wish_id, verified_at)
  VALUES (demo_id, 2, cur, (d - 3)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 hours')
  RETURNING id INTO v;
  INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at) VALUES
    (demo_id, 2, v, 2, (d - 3)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 hours'),
    (demo_id, 2, v, 4, (d - 3)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 hours');

  -- 친구 돌돌이: 9일 전 시작, 돌 9개, 오늘은 아직 안 쌓음, 불국사·석굴암 (방문함, 엽서 2장)
  INSERT INTO wishes (user_id, spot_id, content, action_goal, visibility, status, start_date, end_date, round)
  VALUES (friend_id, 5, '매일 30분 달리기', '아침에 30분 뛰기', 'FRIENDS', 'ACTIVE', d - 9, d + 20, 1)
  RETURNING id INTO fw;
  FOR i IN 0..8 LOOP
    INSERT INTO stones (wish_id, stone_date, type, content)
    VALUES (fw, d - 9 + i, CASE WHEN i % 3 = 1 THEN 'ATTENDANCE' ELSE 'PRACTICE' END,
            CASE WHEN i % 3 = 1 THEN NULL ELSE (3 + i / 2) || 'km 달렸다' END);
  END LOOP;
  INSERT INTO spot_visits (user_id, spot_id, wish_id, verified_at)
  VALUES (friend_id, 5, fw, (d - 6)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '11 hours')
  RETURNING id INTO v;
  INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at) VALUES
    (friend_id, 5, v, 1, (d - 6)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '11 hours'),
    (friend_id, 5, v, 4, (d - 6)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '11 hours');

  -- 주고받은 엽서 (보낸 날 순서대로)
  INSERT INTO postcard_letters (sender_id, receiver_id, spot_id, style, message, read_at, created_at) VALUES
    (demo_id, friend_id, 3, 3, '호미곶에서 해 뜨는 거 보다가 네 생각 났어! 우리 30일 꼭 채우자.',
     now() - interval '6 days', now() - interval '7 days'),
    (friend_id, demo_id, 5, 4, '석굴암 부처님 앞에서 네 취업도 같이 빌었어. 이번엔 꼭 될 거야!',
     now() - interval '4 days', now() - interval '5 days'),
    (demo_id, demo_id, 2, 2, '30일 뒤의 나에게. 과거길에서 다짐한 합격, 끝까지 가자.',
     now() - interval '3 days', now() - interval '3 days'),
    (demo_id, friend_id, 1, 2, '갓바위는 한 가지 소원은 꼭 들어준대. 달리기 소원 같이 빌었어.',
     now() - interval '1 day', now() - interval '2 days');
  INSERT INTO postcard_letters (sender_id, receiver_id, spot_id, style, message, created_at)
  VALUES (friend_id, demo_id, 5, 1, '오늘 9km 뛰었다! 너도 오늘 돌 쌓는 거 잊지 마~', now() - interval '2 hours')
  RETURNING id INTO l;
  INSERT INTO notifications (user_id, actor_id, type, target_id, message, is_read, created_at)
  VALUES (demo_id, friend_id, 'POSTCARD', l, '돌돌이님이 엽서를 보냈어요', false, now() - interval '2 hours');

  -- 소원 캘린더: 어제 끝낸 일, 오늘·앞으로 할 일, 이번 돌탑이 끝난 다음 날 시작할 소원 메모
  INSERT INTO plan_todos (user_id, todo_date, content, done) VALUES
    (demo_id, d - 1, '영어 단어 20개 외우기', true),
    (demo_id, d - 1, '이력서 초안 쓰기', true),
    (demo_id, d, '영어 단어 20개 외우기', false),
    (demo_id, d, '알고리즘 문제 1개 풀기', false),
    (demo_id, d + 1, '포트폴리오 README 다듬기', false),
    (demo_id, d + 3, '모의 면접 영상 보기', false);
  INSERT INTO wish_plans (user_id, content, start_date) VALUES (demo_id, '하프 마라톤 완주하기', d + 18);

  -- 돌돌이의 응원
  INSERT INTO cheers (wish_id, writer_id, message, is_anonymous, created_at) VALUES
    (cur, friend_id, '오늘도 하나 쌓자! 취업 꼭 하자', false, now() - interval '2 days'),
    (cur, friend_id, '영어 단어 벌써 2주째라니 대단해', false, now() - interval '3 hours');

  RAISE NOTICE '시연 계정 준비 완료: 소원지기 %, 진행 중 돌탑 % / 돌돌이 %, 돌탑 %', demo_id, cur, friend_id, fw;
END $$;
