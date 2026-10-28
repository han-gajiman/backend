# API 명세서

> 상태: 10/22 업데이트 초안 · Base URL: `/api/v1` · 인증: `Authorization: Bearer {accessToken}` (JWT)
> 🔓 = 인증 불필요 · 🟡 = 팀 결정 필요 · 📄 = 페이지네이션 적용
> 원본: Notion "API 명세서" (이 파일은 복사본이므로, 큰 변경이 있으면 원본에서 다시 내보내세요)

## API 목록

| ID | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| AUTH-01 | POST | `/auth/kakao` 🔓 | 카카오 로그인/가입 |
| AUTH-01 | POST | `/auth/google` 🔓 | Google 로그인/가입 |
| AUTH-03 | POST | `/auth/refresh` 🔓 | 토큰 재발급 |
| AUTH-03 | POST | `/auth/logout` | 로그아웃 |
| AUTH-02 | POST | `/users/me/onboarding` | 온보딩 (약관 동의 + 닉네임) |
| AUTH-04 | GET | `/users/me` | 내 정보 |
| AUTH-04 | PUT | `/users/me/push-token` | Expo 푸시 토큰 등록 |
| AUTH-05 | PUT | `/users/me/agreements/location` | 위치정보 동의 변경 |
| AUTH-06 | DELETE | `/users/me` | 회원 탈퇴 (즉시 삭제) |
| WISH-02 | POST | `/ai/action-goals` | AI 실천 목표 추천 |
| WISH-03 | POST | `/wishes` | 소원 등록 (돌탑 시작) |
| - | GET | `/wishes/current` | 진행 중 돌탑 |
| WISH-04 | PATCH | `/wishes/{wishId}/visibility` | 공개 범위 변경 |
| STONE-01 | POST / GET | `/wishes/{wishId}/stones` | 오늘의 돌 / 돌 목록 📄 |
| TOWER-02 | POST | `/wishes/{wishId}/reflection` | 30일 완성 회고 |
| TOWER-03 | GET | `/wishes?round=1` | 돌탑 모아보기 |
| SPOT-01 | GET | `/spots` 🔓 | 명소 목록 (category 필터) |
| SPOT-03 | POST | `/spots/{spotId}/visits` | 방문 인증 → 배경 해금 + 엽서 |
| SPOT-04 | GET | `/postcards` | 엽서 보관함 📄 |
| FRIEND-01 | POST | `/friends/invites` | 초대 링크 생성 |
| FRIEND-01 | POST | `/friends/invites/{inviteCode}/accept` | 초대 수락 |
| FRIEND-02 | GET | `/friends` | 친구 목록 + 돌탑 요약 📄 |
| FRIEND-03 | POST / GET | `/wishes/{wishId}/cheers` | 응원 작성 / 목록 📄 |
| FRIEND-04 | POST | `/friends/{friendId}/nudge` | 콕 찌르기 |
| FRIEND-05 | POST | `/wishes/{wishId}/anonymous-keeper` | 익명 소원지기 매칭 🟡 |
| NOTI-01 | GET / PATCH | `/notifications`, `/notifications/{id}/read` | 알림 목록 📄 / 읽음 |
| DEMO-01 | POST | `/demo/time-travel` | 시간 이동 (시연 전용) |

> 기능명세서에 추가된 DEMO-02(위치 인증 우회)는 아직 이 명세서에 엔드포인트가 없습니다. 방식(좌표 고정 / 반경 해제)이 정해지면 추가하세요.

## 상세

### 공통 · 페이지네이션 · 에러 코드

```json
// 성공
{ "success": true, "data": { ... }, "error": null }
// 실패
{ "success": false, "data": null, "error": { "code": "STONE_ALREADY_TODAY", "message": "오늘은 이미 돌을 쌓았어요." } }

// 페이지네이션 (📄 표시 API 공통)
// Request:  ?page=0&size=20   (기본 page=0, size=20, 최대 50)
// Response: { "content": [ ... ], "page": 0, "size": 20, "hasNext": true }

// 에러 코드
401 AUTH_INVALID_TOKEN         Access Token 없음/만료 → /auth/refresh 시도
401 AUTH_INVALID_REFRESH_TOKEN Refresh Token 만료/폐기 → 재로그인
401 AUTH_SOCIAL_FAILED         카카오/Google 토큰 검증 실패
403 ONBOARDING_REQUIRED        온보딩 미완료
400 REQUIRED_AGREEMENT_MISSING 필수 약관 미동의
403 LOCATION_CONSENT_REQUIRED  위치정보 미동의 상태에서 방문 인증
404 WISH_NOT_FOUND             없는 돌탑
409 WISH_ALREADY_ACTIVE        진행 중 돌탑이 있는데 새로 등록
403 WISH_NOT_OWNER             본인 돌탑 아님
400 WISH_NOT_ACTIVE            기간 끝난 돌탑에 돌 쌓기
403 TOWER_NOT_COMPLETED        30일 전 회고 시도
409 STONE_ALREADY_TODAY        오늘 이미 돌 쌓음
400 SPOT_OUT_OF_RANGE          인증 반경 밖
409 SPOT_ALREADY_VISITED_TODAY 같은 명소 오늘 이미 인증
400 FRIEND_SELF_INVITE         본인 초대 수락
409 FRIEND_ALREADY             이미 친구
404 FRIEND_INVITE_NOT_FOUND    잘못되거나 만료된 초대 코드
404 FRIEND_NOT_FOUND           친구가 아닌 회원(본인 포함)에게 콕 찌르기
403 CHEER_NOT_ALLOWED          친구 아님 / 비공개 돌탑
400 CHEER_TOO_LONG             응원 글자 수 초과
400 NUDGE_NOT_ALLOWED          친구 공개 진행 중 돌탑이 없거나 오늘 이미 돌을 쌓은 친구에게 콕 찌르기
429 NUDGE_LIMIT                오늘 이미 콕 찌름
404 NOTIFICATION_NOT_FOUND     없거나 남의 알림 읽음 처리
429 AI_RATE_LIMIT              AI 추천 하루 호출 횟수 초과
// 공통 (10/28 추가)
400 INVALID_INPUT              요청 값 검증 실패, JSON·Content-Type 오류, 파라미터 누락·타입 오류
404 NOT_FOUND                  없는 주소
500 INTERNAL_ERROR             서버 오류
```

### 🔐 Auth · 회원

```json
// POST /auth/kakao 🔓   앱의 카카오 SDK에서 받은 토큰을 서버가 검증
{ "accessToken": "카카오_액세스_토큰" }
// POST /auth/google 🔓   앱의 Google 로그인에서 받은 ID 토큰을 서버가 검증 (10/27: Apple 대신 Google)
{ "idToken": "eyJhbGciOiJSUzI1NiIs..." }
// Response 200 (공통)
{ "accessToken": "eyJhbGciOi...", "refreshToken": "dGhpcyBpcyBh...",
  "isNewUser": true, "onboarded": false }
// onboarded=false → 프론트는 온보딩 화면으로

// POST /auth/refresh 🔓   재발급 시 Refresh Token도 새로 교체 (기존 건 폐기)
{ "refreshToken": "dGhpcyBpcyBh..." }
→ 200 { "accessToken": "...", "refreshToken": "..." }

// POST /auth/logout
{ "refreshToken": "dGhpcyBpcyBh..." }   → 204

// POST /users/me/onboarding   TERMS·PRIVACY 필수, LOCATION 선택
{ "nickname": "석현",
  "agreements": { "terms": true, "privacy": true, "location": false },
  "agreementVersion": "1.0" }
→ 204

// GET /users/me
{ "id": 1, "nickname": "석현", "profileImageUrl": "https://...", "provider": "KAKAO",
  "agreements": { "location": false }, "hasActiveWish": true,
  "journey": { "startedOn": "2026-10-22", "currentRound": 1, "reviewAt": "2027-10-22" } }
// journey: 첫 돌탑 전이면 null

// PUT /users/me/push-token
{ "pushToken": "ExponentPushToken[xxxx]" }   → 204

// PUT /users/me/agreements/location
{ "agreed": true }   → 204

// DELETE /users/me   즉시 삭제
// 1) 카카오 연결 끊기 (서버, KAKAO_ADMIN_KEY) / Google은 서버에 Google 토큰이 없어 앱에서 revokeAccess
// 2) Refresh Token 폐기  3) 본인 데이터·친구 관계·작성한 응원·관련 알림 삭제
→ 204

// 온보딩 전에는 로그인·재발급·로그아웃, GET·DELETE /users/me, 온보딩, 푸시 토큰만 호출 가능
// 그 외 로그인 필요 API는 403 ONBOARDING_REQUIRED
```

### 🌠 Wish (돌탑)

```json
// POST /ai/action-goals  (P1)
// 타임아웃 5초, 1인 하루 10회, LLM 실패·타임아웃 시 기본 추천 문구 반환 (fallback=true)
{ "wish": "취업하고 싶어" }
→ 200 { "suggestions": ["매일 영어 단어 20개 외우기", "매일 코딩 테스트 1문제 풀기", "주 3회 이력서 1곳 지원하기"],
        "fallback": false }

// POST /wishes   진행 중 돌탑 있으면 409, 등록 후 수정 API 없음. 카테고리는 명소에서 파생
{ "content": "백엔드 개발자로 취업하기", "actionGoal": "매일 영어 단어 20개 외우기",
  "spotId": 2, "visibility": "FRIENDS" }
→ 201 { "wishId": 10, "startDate": "2026-10-22", "endDate": "2026-11-20" }

// GET /wishes/current   없으면 data: null
{ "wishId": 10, "content": "백엔드 개발자로 취업하기", "actionGoal": "매일 영어 단어 20개 외우기",
  "status": "ACTIVE", "visibility": "FRIENDS",
  "spot": { "id": 2, "name": "문경새재 과거길", "category": "PASS", "visited": false, "backgroundImageUrl": null },
  "stoneCount": 12, "stonedToday": false, "dayIndex": 13, "daysLeft": 17 }
// visited=false → backgroundImageUrl null → 프론트 허허벌판 기본 배경

// PATCH /wishes/{wishId}/visibility
{ "visibility": "PRIVATE" }   → 204

// POST /wishes/{wishId}/reflection   status=COMPLETED일 때만, 응답 후 CLOSED
{ "result": "ACHIEVED", "reflection": "한 달 동안 단어 600개 외움" }   → 204

// GET /wishes?round=1   회차별 돌탑 모아보기 (첫 시작일부터 1년 = 1회차, 최대 12개라 페이지네이션 없음)
// round 생략 시 현재 회차
[ { "wishId": 10, "content": "백엔드 개발자로 취업하기", "startDate": "2026-10-22",
    "stoneCount": 27, "spotName": "문경새재 과거길", "visited": true, "result": "ACHIEVED" } ]
```

### 🪨 Stone

```json
// POST /wishes/{wishId}/stones   오늘(KST) 1개만
// content 있으면 PRACTICE(실천 기록), 없으면 ATTENDANCE(출석). 둘 다 돌 1개
{ "content": "영어 단어 20개 외움" }
→ 201 { "stoneId": 101, "stoneDate": "2026-10-22", "type": "PRACTICE", "stoneCount": 13 }

// GET /wishes/{wishId}/stones?page=0&size=20 📄   본인 또는 (친구 공개 시) 친구, 최신순
{ "content": [ { "stoneId": 101, "stoneDate": "2026-10-22", "type": "PRACTICE", "content": "영어 단어 20개 외움" } ],
  "page": 0, "size": 20, "hasNext": false }
```

### 🗺️ Spot & Postcard

```json
// GET /spots?category=PASS 🔓
[ { "id": 2, "name": "문경새재 과거길", "region": "문경", "category": "PASS",
    "description": "경사스러운 소식을 듣는 길", "latitude": 36.7, "longitude": 128.1 } ]

// POST /spots/{spotId}/visits   위치정보 동의 필수, 좌표는 거리 계산에만 쓰고 저장 X
// 진행 중 돌탑 명소면 배경 해금, 처음 방문이면 엽서 지급
{ "latitude": 36.7001, "longitude": 128.1002 }
→ 201 { "visitId": 5, "distanceM": 42, "backgroundUnlocked": true,
        "postcard": { "postcardId": 3, "imageUrl": "https://..." } }
// 실패: 403 LOCATION_CONSENT_REQUIRED, 400 SPOT_OUT_OF_RANGE (distanceM 포함)

// GET /postcards?page=0&size=20 📄
{ "content": [ { "postcardId": 3, "spotName": "문경새재 과거길", "imageUrl": "https://...",
                 "issuedAt": "2026-10-22T15:10:00+09:00" } ],
  "page": 0, "size": 20, "hasNext": false }
```

### 🤝 Friend & Cheer

```json
// POST /friends/invites   https 링크 (App Links / Universal Links)
// 앱 설치 시 앱으로 열림, 미설치 시 스토어로 이동. 도메인 🟡
→ 201 { "inviteCode": "a8Kx2pQ", "inviteUrl": "https://{도메인}/invite/a8Kx2pQ",
        "expiresAt": "2026-10-29T09:00:00+09:00" }

// POST /friends/invites/{inviteCode}/accept
→ 200 { "friendId": 1, "nickname": "석현" }

// GET /friends?page=0&size=20 📄   친구 돌탑이 PRIVATE면 wish: null
{ "content": [ { "friendId": 4, "nickname": "지기",
                 "wish": { "wishId": 12, "content": "토익 800점", "stoneCount": 8, "stonedToday": false } } ],
  "page": 0, "size": 20, "hasNext": false }

// POST /wishes/{wishId}/cheers   친구(친구 공개 돌탑) 또는 익명 소원지기만, 작성 시 푸시
// 진행 중(ACTIVE) 돌탑만. 본인 돌탑·친구 아님·비공개 → 403 CHEER_NOT_ALLOWED, 끝난 돌탑 → 400 WISH_NOT_ACTIVE
// 앞뒤 공백 제거 후 1~100자 (빈 문자열 400 INVALID_INPUT, 초과 400 CHEER_TOO_LONG)
{ "message": "오늘도 하나 쌓자!" }   → 201 { "cheerId": 7 }

// GET /wishes/{wishId}/cheers?page=0&size=20 📄   익명이면 writerNickname null, 최신순
// 돌탑 주인 또는 친구 공개 돌탑의 친구만 (그 외 403 CHEER_NOT_ALLOWED)
{ "content": [ { "cheerId": 7, "writerNickname": "지기", "isAnonymous": false,
                 "message": "오늘도 하나 쌓자!", "createdAt": "..." } ],
  "page": 0, "size": 20, "hasNext": false }

// POST /friends/{friendId}/nudge   같은 친구 하루 1회(KST) → 204
// 친구 공개 진행 중 돌탑이 있고 오늘 아직 안 쌓은 친구만. 받는 사람 알림함에 NUDGE 알림 저장
// 친구 아님 404 FRIEND_NOT_FOUND, 대상 아님 400 NUDGE_NOT_ALLOWED, 오늘 이미 찌름 429 NUDGE_LIMIT
```

### 🔔 Notification & Demo

```json
// GET /notifications?page=0&size=20 📄
{ "content": [ { "id": 3, "type": "TOWER_COMPLETED", "targetId": 10,
                 "message": "30일 돌탑이 완성됐어요", "isRead": false, "createdAt": "..." } ],
  "page": 0, "size": 20, "hasNext": false }

// PATCH /notifications/{id}/read   → 204   (없거나 남의 알림 404 NOTIFICATION_NOT_FOUND, 이미 읽었어도 204)

// 푸시 (10/28): 알림이 저장·커밋된 뒤 받는 사람의 Expo 푸시 토큰(PUT /users/me/push-token)으로 발송
// { title: "한 가지만", body: 알림 message, data: { type, targetId } }. 토큰이 없으면 알림함에만 저장

// POST /demo/time-travel   서버 시계 N일 이동 후 완성 스케줄러 즉시 실행 (시연 프로필 전용)
{ "days": 30 }   → { "serverNow": "2026-11-21T09:00:00+09:00" }
```
