-- 11/10 카카오·Google 프로필 사진 URL은 화면에서 쓰지 않아서 더 이상 저장하지 않습니다 (개인정보 처리방침: 안 쓰는 정보는 모으지 않음).
-- 이미 저장된 값도 지웁니다. 컬럼은 남겨 두고(응답 형식 유지) 항상 비어 있습니다.
UPDATE users SET profile_image_url = NULL WHERE profile_image_url IS NOT NULL;
