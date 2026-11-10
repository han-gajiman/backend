# backend

**한 가지만**(2026 단디톤 출품작)의 API 서버입니다. 소원 하나를 정하고, 30일 동안 매일 돌을 하나씩 쌓으며, 경북 명소를 방문해 이어가는 서비스입니다.

- 실서비스 웹: https://han-gajiman.github.io (이 서버에 연결됨, API `https://hangajiman.duckdns.org/api/v1`)
- 데모 웹: https://han-gajiman.expo.app (서버 없이 예시 데이터로 동작)
- 앱·웹 코드: [frontend](https://github.com/han-gajiman/frontend)
- 기준 문서: `docs/기능명세서.md` · `docs/API명세서.md` · `docs/erd_final.sql` (노션 원본과 같은 내용)
- 개발 규칙: `CLAUDE.md` · 서버 배포: `deploy/README.md`

## 기술 스택

- Java 17 · Spring Boot 3.3.5 · Gradle
- Spring Data JPA · PostgreSQL 16 · Flyway (마이그레이션 V1~V14)
- Spring Security + JWT (Access/Refresh Token, 재발급할 때마다 교체)
- Claude API (`com.anthropic:anthropic-java`, 기본 모델 `claude-haiku-5-5`)
- 배포: AWS EC2 + Docker Compose + Caddy(HTTPS 자동 인증서)

## 기능

모든 API는 `/api/v1` 아래에 있고 같은 형식(`{ success, data, error }`)으로 응답합니다. 요청·응답 예시와 에러 코드는 `docs/API명세서.md`에 있습니다.

| 영역 | 기능 | 주요 API |
| --- | --- | --- |
| 회원 (AUTH) | 카카오·Google 로그인(앱 SDK, 웹은 로그인 화면을 거치는 방식), 이메일 회원가입·로그인, 아이디·비밀번호 찾기(메일 인증코드), 토큰 재발급·로그아웃, 온보딩(약관·닉네임), 닉네임 변경(회원끼리 중복 불가), 탈퇴(즉시 삭제) | `/auth/*`, `/users/me*` |
| 소원 (WISH) | 소원 + 실천 목표 등록(수정 불가, 진행 중 돌탑 1개), 공개 범위, AI 실천 목표 3개 + 명소 추천 | `/wishes`, `/wishes/current`, `/wishes/suggestions` |
| 돌 (STONE) | 오늘의 돌(하루 1개, KST), 돌 목록, 쌓을 때 기록에 넣은 오늘 할 일 완료 체크 | `/wishes/{id}/stones` |
| 돌탑 (TOWER) | 30일 완성 처리(매시간), 완성 회고, 1년 단위 모아보기, 1년 회고(첫 시작일 +365일마다 알림) | `/wishes/{id}/reflection`, `/wishes?round=`, `/wishes/rounds*` |
| 명소 (SPOT) | 명소 10곳과 이야기, 방문 인증(위치정보 없이 결과만), 돌탑마다 명소별 한 번 다시 인증 | `/spots`, `/spots/{id}/visits` |
| 엽서 (SPOT-04) | 인증할 때마다 명소 엽서 4종 중 없는 1장, 엽서 보내기(친구·나에게, 100자), 받은·보낸 엽서 | `/postcards*` |
| 친구 (FRIEND) | 초대 링크, 친구 목록과 돌탑 요약, 응원(100자), 콕 찌르기(하루 1회) | `/friends*`, `/wishes/{id}/cheers` |
| 알림 (NOTI) | 알림함, 읽음, Expo 푸시 발송 | `/notifications*` |
| 소원 캘린더 (PLAN) | 달력(돌탑·소원 메모·할 일), 날짜별 할 일(하루 5개), 앞으로 할 소원 메모, AI 할 일 추천(하루 10번) | `/plans/*` |
| 시연 (DEMO) | 시연용 시간 이동, 시연 계정을 처음 세팅한 상태로 되돌리기 | `/demo/*` (`local`·`demo` 프로필만) |

- 온보딩 전에는 로그인·회원 API만 쓸 수 있고, 나머지는 `403 ONBOARDING_REQUIRED`입니다.
- **위치정보는 받지 않습니다.** 방문 인증은 앱이 기기 안에서 반경을 확인하고, 서버에는 결과만 보냅니다.
- **AI는 서버에서만 부릅니다.** 타임아웃 5초, 재시도 없음, 종류별 1인 하루 10회. 소원 글은 저장하지 않고 호출 시각만 `ai_suggestion_logs`에 남깁니다. 실패하면 기본 문구로 대체하고, 위기 단어가 있는 소원은 AI 대신 도움 안내를 돌려줍니다.

## 로컬에서 실행하기

### 1. DB 준비 (macOS 예시)

```bash
brew install postgresql@16
brew services start postgresql@16
createdb hangajiman        # 개발용
createdb hangajiman_test   # 테스트용
```

테이블은 앱을 켜면 Flyway가 만듭니다. 기본 접속 정보는 `localhost:5432`, OS 사용자 이름, 비밀번호 없음(Homebrew 기본)입니다. 다르면 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TEST_DB_URL` 환경변수로 바꾸세요.

### 2. 실행·테스트

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # http://localhost:8080
./gradlew test                                               # 테스트 (hangajiman_test DB)
curl localhost:8080/api/health                               # { "success": true, "data": { "status": "UP" } }
```

`local` 프로필은 개발용 로그인, 서버 전체 시간 이동, `localhost` 모든 포트의 CORS를 켭니다.

### 3. 로그인이 필요한 API 부르기

카카오·Google 없이 개발용 로그인으로 실제와 같은 토큰을 받습니다 (`local` 전용).

```bash
curl -X POST localhost:8080/api/v1/dev/login -H 'Content-Type: application/json' -d '{"devKey":"me"}'
# → accessToken으로 온보딩부터
curl -H "Authorization: Bearer {accessToken}" -H 'Content-Type: application/json' \
  -d '{"nickname":"소원지기","agreements":{"terms":true,"privacy":true},"agreementVersion":"1.0"}' \
  localhost:8080/api/v1/users/me/onboarding
```

같은 `devKey`는 같은 회원입니다. 이메일 회원가입(`/auth/signup/local`)으로 만들어도 됩니다.

### 4. AI 추천을 실제로 부르려면

`backend/.env.local`(git에 안 올라감)에 `ANTHROPIC_API_KEY=...`를 넣고 불러와서 실행합니다. 키가 없으면 AI를 부르지 않고 기본 문구를 돌려줍니다.

```bash
set -a && . ./.env.local && set +a && ./gradlew bootRun --args='--spring.profiles.active=local'
```

## 시연용 기능

| | 로컬 (`local`) | 배포 서버 (`demo`) |
| --- | --- | --- |
| 시간 이동 `POST /demo/time-travel {days}` | 서버 전체 날짜가 함께 이동 | `DEMO_TIME_TRAVEL_EMAILS`의 계정들(기본: 시연 계정 + 친구 계정)만 한 묶음으로 이동. 다른 계정은 날짜 그대로이고 호출하면 `403 DEMO_NOT_ALLOWED` |
| 원래 날짜로 `POST /demo/time-travel/reset` | 전체 | 그 묶음만 |
| 처음 상태로 `POST /demo/reset-scenario` | - | 시연 계정 본인만. `resources/demo/seed-demo.sql`을 실행해서 두 계정의 기록·알림·날짜를 처음 세팅으로 |

- 30일이 지난 돌탑은 이동하는 즉시 완성 처리하고 알림을 보냅니다. 로그인 토큰은 실제 시간 기준이라 만료되지 않습니다.
- 이동한 날짜는 메모리에만 있어서 서버를 다시 켜면 실제 날짜로 돌아옵니다.
- 시연 계정에 무엇이 들어 있는지는 `src/main/resources/demo/seed-demo.sql` 맨 위 설명을 보세요.

## 환경변수

| 이름 | 용도 | 없으면 |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | DB 접속 | 로컬 기본값 |
| `JWT_SECRET` | JWT 서명 키 (`openssl rand -base64 48`) | 로컬은 개발용 기본값, `demo` 프로필은 **필수**(없으면 서버가 안 켜짐) |
| `KAKAO_ADMIN_KEY` | 탈퇴 시 카카오 연결 끊기 | 연결 끊기만 건너뜀 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 웹 카카오 로그인 (인가 코드 → 토큰). Client Secret은 콘솔에서 켰을 때만 | 웹 카카오 로그인 불가 |
| `GOOGLE_CLIENT_IDS` | Google ID 토큰을 받아 줄 클라이언트 ID (웹·Android·iOS, 쉼표로) | Google 로그인 불가 |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | AI 추천 (모델 기본 `claude-haiku-5-5`) | 기본 문구로 대체 |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | 비밀번호 찾기 메일 (Gmail은 앱 비밀번호) | 인증코드를 서버 로그에만 찍음 |
| `INVITE_BASE_URL` | 친구 초대 링크 앞부분 (웹 주소) | `http://localhost:8080` |
| `CORS_ALLOWED_ORIGINS` | 브라우저 요청을 허용할 웹 주소 (쉼표로) | `demo`: github.io·expo.app 주소 |
| `DEMO_TIME_TRAVEL_EMAILS` | 시간 이동 묶음 계정 (쉼표로) | `demo@hangajiman.app,friend@hangajiman.app` |
| `EXPO_ACCESS_TOKEN` | Expo 푸시 보안을 켰을 때만 | 인증 없이 발송 |

배포 서버는 `deploy/.env`(git에 안 올라감)에 넣습니다. 예시는 `deploy/.env.example`입니다.

## 배포

EC2 한 대에 앱·PostgreSQL·Caddy를 Docker Compose로 띄웁니다. 처음 설치와 업데이트 방법은 `deploy/README.md`에 있습니다.

```bash
cd ~/backend/deploy && git pull && docker compose up -d --build app   # 업데이트
```

DB 마이그레이션은 앱이 켜질 때 Flyway가 자동으로 적용합니다.

## DB 마이그레이션 (Flyway)

`src/main/resources/db/migration/`

| 버전 | 내용 |
| --- | --- |
| V1 | 전체 테이블 (`docs/erd_final.sql`의 처음 모습) |
| V2, V4 | 명소 시드 10곳 |
| V3 | 소셜 로그인을 카카오 + Google로 |
| V5, V6 | 이메일 회원가입·로그인, 비밀번호 찾기 인증코드 |
| V7 | 방문 인증에서 위치정보 컬럼 삭제 |
| V8 | 1년 회고 |
| V9 | 엽서 4종(style), 엽서 보내기(`postcard_letters`), 돌탑마다 명소 재인증 |
| V10, V11 | 명소 이야기 |
| V12 | 소원 캘린더(`plan_todos`, `wish_plans`), AI 호출 기록 종류(`kind`) |
| V13 | 닉네임 중복 금지 (`uq_users_nickname`) |
| V14 | 카카오·Google 프로필 사진 URL을 저장하지 않음 (기존 값 삭제) |

- 이미 적용된 파일은 **수정하지 말고** 새 버전 파일을 추가합니다. ERD 문서(`docs/erd_final.sql`)도 같이 고칩니다.
- JPA는 `ddl-auto=validate`라 엔티티와 테이블이 다르면 앱이 켜지지 않습니다.
- 로컬 DB를 처음부터: `dropdb hangajiman && createdb hangajiman`

## 폴더 구조

```text
src/main/java/com/hangajiman/
├── global/        공통 (응답 형식, 예외·에러 코드, BaseEntity, Clock(DemoClock), CORS, 보안: JWT·온보딩 확인)
├── auth/          로그인(카카오·Google·이메일), 웹 카카오 로그인, 토큰, 계정 찾기 메일, 탈퇴
├── user/          회원, 온보딩, 약관 동의 이력, 내 정보, 닉네임 변경
├── wish/          소원(돌탑), 30일 완성, 회고, 모아보기, 1년 회고
├── stone/         돌
├── spot/          명소, 방문 인증
├── postcard/      엽서, 엽서 보내기·받기
├── friend/        초대, 친구, 응원, 콕 찌르기
├── notification/  알림함, Expo 푸시 (알림은 NotificationService.notify()로만 만듦)
├── plan/          소원 캘린더 (할 일, 소원 메모, AI 할 일 추천)
├── ai/            Claude 호출(WishSuggester), 추천 서비스, 호출 기록
└── demo/          시연용 시간 이동, 시연 계정 초기화 (local·demo 프로필)
src/main/resources/
├── application.properties, application-local.properties, application-demo.properties
├── db/migration/  Flyway
└── demo/seed-demo.sql  시연 계정 처음 세팅 (deploy/seed-demo.sql은 이 파일의 링크)
```
