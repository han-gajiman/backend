-- 시연용 계정(demo@hangajiman.app)에 지난 기록을 채웁니다. 계정·친구 계정·친구 관계는 API로 먼저 만들어 둡니다.
-- 실행: cd ~/backend/deploy && docker compose exec -T db psql -U hangajiman -d hangajiman < seed-demo.sql
-- 다시 실행하면 이 계정의 돌탑·방문·엽서·캘린더·응원을 지우고 오늘 기준으로 새로 만듭니다 (다른 회원은 건드리지 않음).
--
-- 만들어지는 상태 (오늘 기준):
--   첫 번째 소원(1년 끝남, 1년 회고 쓰기 가능): 지난 돌탑 8개
--   두 번째 소원: 지난 돌탑 6개 + 진행 중 돌탑 1개 (13일째, 돌 12개, 오늘은 아직 안 쌓음, 영덕 방문함, 친구 공개)
--   명소 방문 3곳(갓바위·호미곶·영덕), 엽서 6장, 소원 캘린더 할 일·소원 메모, 친구 돌돌이의 응원 2개

DO $$
DECLARE
  demo_id   BIGINT;
  friend_id BIGINT;
  d         DATE := (now() AT TIME ZONE 'Asia/Seoul')::date;
  j         DATE := (now() AT TIME ZONE 'Asia/Seoul')::date - 569;  -- 첫 돌탑 시작일 (1년 + 204일 전)
  w         BIGINT;
  cur       BIGINT;
  s         DATE;
  n         INT;
  i         INT;
  k         INT;
  v         BIGINT;
  wishes_txt TEXT[] := ARRAY['토익 850점 넘기','매일 아침 운동하기','책 12권 읽기','자격증 따기','일찍 일어나기',
                             '영어 회화 늘리기','블로그 꾸준히 쓰기','다이어트 5kg','코딩 테스트 통과하기','부모님께 매주 전화하기',
                             '포트폴리오 완성하기','기타 한 곡 완주하기','물 2L 마시기','하루 만 보 걷기'];
  goals_txt TEXT[] := ARRAY['매일 단어 30개 외우기','아침 20분 스트레칭','매일 20쪽 읽기','매일 기출 10문제','6시 30분 기상',
                            '매일 10분 쉐도잉','이틀에 한 편 쓰기','저녁 7시 이후 금식','매일 알고리즘 1문제','일요일 저녁 전화',
                            '매일 커밋 1개','매일 30분 연습','하루 물 8잔','점심 후 산책'];
  notes_txt TEXT[] := ARRAY['오늘도 해냈다.','귀찮았지만 끝까지 했다.','아침에 바로 끝내니 마음이 편하다.','생각보다 재밌었다.',
                            '내일은 더 일찍 해야지.','조금씩 익숙해지는 느낌이다.'];
  refl_txt  TEXT[] := ARRAY['매일 쌓으니 끝까지 해냈다','중간에 쉬었지만 다시 돌아왔다','습관이 된 것 같아 뿌듯하다',
                            '생각보다 어려웠지만 배운 게 많다'];
BEGIN
  SELECT id INTO demo_id FROM users WHERE email = 'demo@hangajiman.app';
  SELECT id INTO friend_id FROM users WHERE email = 'friend@hangajiman.app';
  IF demo_id IS NULL THEN
    RAISE EXCEPTION '시연 계정(demo@hangajiman.app)이 없어요. 먼저 회원가입해 주세요.';
  END IF;

  -- 이 계정의 기록만 비우기 (돌·방문·엽서·응원은 돌탑 삭제로 함께 지워짐)
  DELETE FROM postcards WHERE user_id = demo_id;
  DELETE FROM spot_visits WHERE user_id = demo_id;
  DELETE FROM wishes WHERE user_id = demo_id;
  DELETE FROM plan_todos WHERE user_id = demo_id;
  DELETE FROM wish_plans WHERE user_id = demo_id;
  DELETE FROM year_reviews WHERE user_id = demo_id;

  UPDATE users SET journey_started_on = j WHERE id = demo_id;

  -- 지난 돌탑 14개 (첫 번째 소원 8개: 30일 간격 / 두 번째 소원 6개: 1년 + 10일부터 30일 간격)
  FOR k IN 0..13 LOOP
    s := CASE WHEN k < 8 THEN j + 30 * k ELSE j + 375 + 30 * (k - 8) END;
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

    -- 첫 번째 돌탑(갓바위)·세 번째 돌탑(호미곶) 때 방문 인증 + 엽서
    IF k = 0 OR k = 2 THEN
      INSERT INTO spot_visits (user_id, spot_id, wish_id, verified_at)
      VALUES (demo_id, (k % 10) + 1, w, (s + 5)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '15 hours')
      RETURNING id INTO v;
      INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at)
      SELECT demo_id, (k % 10) + 1, v, st, (s + 5)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '15 hours'
      FROM unnest(CASE WHEN k = 0 THEN ARRAY[1, 2] ELSE ARRAY[1, 3, 4] END) AS st;
    END IF;
  END LOOP;

  -- 진행 중 돌탑: 12일 전 시작, 돌 12개, 오늘은 아직 안 쌓음 (시연에서 쌓기), 영덕
  INSERT INTO wishes (user_id, spot_id, content, action_goal, visibility, status, start_date, end_date, round)
  VALUES (demo_id, 8, '백엔드 개발자로 취업하기', '매일 영어 단어 20개 외우기', 'FRIENDS', 'ACTIVE', d - 12, d + 17,
          ((d + 17) - j) / 365 + 1)
  RETURNING id INTO cur;
  FOR i IN 0..11 LOOP
    INSERT INTO stones (wish_id, stone_date, type, content)
    VALUES (cur, d - 12 + i,
            CASE WHEN i % 3 = 2 THEN 'ATTENDANCE' ELSE 'PRACTICE' END,
            CASE WHEN i % 3 = 2 THEN NULL WHEN i % 2 = 0 THEN '영어 단어 20개 외움' ELSE notes_txt[(i % 6) + 1] END);
  END LOOP;
  INSERT INTO spot_visits (user_id, spot_id, wish_id, verified_at)
  VALUES (demo_id, 8, cur, (d - 3)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 hours')
  RETURNING id INTO v;
  INSERT INTO postcards (user_id, spot_id, visit_id, style, issued_at)
  VALUES (demo_id, 8, v, 2, (d - 3)::timestamp AT TIME ZONE 'Asia/Seoul' + interval '10 hours');

  -- 소원 캘린더: 어제 끝낸 일, 오늘·앞으로 할 일, 이번 돌탑이 끝난 다음 날 시작할 소원 메모
  INSERT INTO plan_todos (user_id, todo_date, content, done) VALUES
    (demo_id, d - 1, '영어 단어 20개 외우기', true),
    (demo_id, d - 1, '이력서 초안 쓰기', true),
    (demo_id, d, '영어 단어 20개 외우기', false),
    (demo_id, d, '알고리즘 문제 1개 풀기', false),
    (demo_id, d + 1, '포트폴리오 README 다듬기', false),
    (demo_id, d + 3, '모의 면접 영상 보기', false);
  INSERT INTO wish_plans (user_id, content, start_date) VALUES (demo_id, '하프 마라톤 완주하기', d + 18);

  -- 친구 돌돌이의 응원
  IF friend_id IS NOT NULL THEN
    INSERT INTO cheers (wish_id, writer_id, message, is_anonymous, created_at) VALUES
      (cur, friend_id, '오늘도 하나 쌓자! 취업 꼭 하자', false, now() - interval '2 days'),
      (cur, friend_id, '영어 단어 벌써 2주째라니 대단해', false, now() - interval '3 hours');
  END IF;

  RAISE NOTICE '시연 계정 준비 완료: 회원 %, 진행 중 돌탑 %', demo_id, cur;
END $$;
