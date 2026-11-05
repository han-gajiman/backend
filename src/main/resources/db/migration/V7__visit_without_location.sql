-- 위치 정보를 서버에 보내지 않습니다 (11/5). 앱이 기기 안에서 반경을 계산하고, 서버는 인증 결과만 받습니다.

-- 거리는 위치에서 나온 값이라 저장하지 않습니다.
ALTER TABLE spot_visits DROP COLUMN distance_m;

-- 위치 동의는 서버에 저장하지 않습니다. (기기 권한으로 처리)
DELETE FROM user_agreements WHERE type = 'LOCATION';
ALTER TABLE user_agreements DROP CONSTRAINT user_agreements_type_check;
ALTER TABLE user_agreements ADD CONSTRAINT user_agreements_type_check CHECK (type IN ('TERMS','PRIVACY'));

-- 회원당 명소 인증은 하나만 (가장 처음 기록을 남김)
DELETE FROM spot_visits a USING spot_visits b
 WHERE a.user_id = b.user_id AND a.spot_id = b.spot_id AND a.id > b.id;
DROP INDEX idx_spot_visits_user_spot;
ALTER TABLE spot_visits ADD CONSTRAINT uq_spot_visits_user_spot UNIQUE (user_id, spot_id);
