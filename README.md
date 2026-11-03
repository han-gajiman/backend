# backend

한 가지만(2026 단디톤) 백엔드입니다.
소원 하나를 정하고, 30일 동안 매일 돌을 쌓으며 실천하고, 경북 명소를 방문해 이어가는 서비스입니다.

- 개발 규칙: `CLAUDE.md`
- 기준 문서: `docs/기능명세서.md` · `docs/API명세서.md` · `docs/erd_final.sql`

## 구현 현황

| 기능 ID | API | 인증 | 상태 |
| --- | --- | --- | --- |
| - | `GET /api/health` | 불필요 | ✅ |
| SPOT-01 | `GET /api/v1/spots?category=` 명소 목록 (category 생략 시 전체) | 불필요 | ✅ |
| AUTH-01 | `POST /api/v1/auth/kakao`, `POST /api/v1/auth/google` 소셜 로그인·자동 가입 | 불필요 | ✅ |
| AUTH-03 | `POST /api/v1/auth/refresh` 토큰 재발급 | 불필요 | ✅ |
| AUTH-03 | `POST /api/v1/auth/logout` 로그아웃 | 필요 | ✅ |
| AUTH-02 | `POST /api/v1/users/me/onboarding` 온보딩 | 필요 | ✅ |
| AUTH-04 | `GET /api/v1/users/me`, `PUT /api/v1/users/me/push-token` | 필요 | ✅ |
| AUTH-05 | `PUT /api/v1/users/me/agreements/location` | 필요 | ✅ |
| AUTH-06 | `DELETE /api/v1/users/me` 회원 탈퇴 | 필요 | ✅ |
| WISH-03 | `POST /api/v1/wishes` 소원 등록 (돌탑 시작) | 필요 | ✅ |
| - | `GET /api/v1/wishes/current` 진행 중 돌탑 | 필요 | ✅ |
| STONE-01 | `POST /api/v1/wishes/{wishId}/stones` 오늘의 돌 (하루 1개) | 필요 | ✅ |
| STONE-01 | `GET /api/v1/wishes/{wishId}/stones?page=&size=` 돌 목록 (최신순, 본인·친구 공개) | 필요 | ✅ |
| TOWER-01 | 30일 완성 처리 (매시간 자동, 완성 알림 생성) | - | ✅ |
| WISH-04 | `PATCH /api/v1/wishes/{wishId}/visibility` 공개 범위 변경 (PRIVATE·FRIENDS, 본인만) | 필요 | ✅ |
| TOWER-02 | `POST /api/v1/wishes/{wishId}/reflection` 완성 회고 (→ CLOSED, 새 소원 가능) | 필요 | ✅ |
| TOWER-04 | 1년 회고 알림 (첫 시작일 +365일마다, 회차당 한 번, 매시간 확인·시간 이동 직후 실행) | - | ✅ |
| TOWER-03 | `GET /api/v1/wishes?round=` 회차별 돌탑 모아보기 (생략 시 현재 회차) | 필요 | ✅ |
| DEMO-01 | `POST /api/v1/demo/time-travel` 시연용 시간 이동 (`local`·`demo` 프로필 전용) | 필요 | ✅ |
| FRIEND-01 | `POST /api/v1/friends/invites` 초대 링크 생성, `POST /api/v1/friends/invites/{inviteCode}/accept` 초대 수락 | 필요 | ✅ |
| FRIEND-02 | `GET /api/v1/friends?page=&size=` 친구 목록 + 돌탑 요약 | 필요 | ✅ |
| FRIEND-03 | `POST`·`GET /api/v1/wishes/{wishId}/cheers` 응원 작성·목록 | 필요 | ✅ |
| FRIEND-04 | `POST /api/v1/friends/{friendId}/nudge` 콕 찌르기 (하루 1회) | 필요 | ✅ |
| NOTI-01 | `GET /api/v1/notifications?page=&size=` 알림함, `PATCH /api/v1/notifications/{id}/read` 읽음 + Expo 푸시 발송 (완성·응원·콕 찌르기) | 필요 | ✅ |
| - | `GET /api/v1/dev/token?userId=` 개발용 토큰 (`local` 프로필 전용) | 불필요 | ✅ |
| - | `POST /api/v1/dev/login { devKey }` 개발용 로그인·자동 가입, 실제와 같은 Access/Refresh Token (`local` 전용) | 불필요 | ✅ |

- 온보딩 전에는 위 회원 API·로그인·로그아웃·탈퇴만 쓸 수 있고, 나머지는 `403 ONBOARDING_REQUIRED`입니다.
- 나머지 기능은 기능명세서 P0 → P1 → P2 순서로 추가합니다.

## 기술 스택

- Java 17 · Spring Boot 3.3.5 · Gradle
- Spring Data JPA · PostgreSQL · Flyway (DB 마이그레이션)

## 처음 받았을 때: 로컬 DB 준비

PostgreSQL이 필요합니다. (macOS 예시)

```bash
brew install postgresql@16
brew services start postgresql@16

createdb hangajiman        # 개발용
createdb hangajiman_test   # 테스트용
```

테이블은 앱을 실행하면 Flyway가 자동으로 만듭니다. 직접 SQL을 실행할 필요는 없습니다.

접속 정보 기본값은 `localhost:5432`, 사용자는 OS 사용자 이름, 비밀번호 없음입니다. (Homebrew 기본 설정)
다르면 환경변수로 바꾸세요.

```bash
export DB_URL=jdbc:postgresql://localhost:5432/hangajiman
export DB_USERNAME=postgres
export DB_PASSWORD=비밀번호
export TEST_DB_URL=jdbc:postgresql://localhost:5432/hangajiman_test   # 테스트 DB
```

## 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'   # http://localhost:8080 (개발용 토큰 API 포함)
./gradlew bootRun --args='--spring.profiles.active=local --server.port=8081'   # 8080을 다른 앱이 쓰고 있을 때
./gradlew test                                               # 테스트 (hangajiman_test DB 사용)
```

### 로그인이 필요한 API를 로컬에서 호출하기

카카오·Google 없이 개발용 토큰으로 호출합니다. (`local` 프로필에서만 동작)

```bash
# 1) 회원이 없으면 하나 만들기
psql -d hangajiman -c "insert into users(provider, provider_user_id) values ('KAKAO', 'dev-1') returning id;"
# 2) 그 id로 토큰 받기
curl "localhost:8080/api/v1/dev/token?userId=1"
# 3) 토큰으로 호출 (온보딩부터)
curl -H "Authorization: Bearer {토큰}" -H "Content-Type: application/json" \
  -d '{"nickname":"소원지기","agreements":{"terms":true,"privacy":true,"location":false},"agreementVersion":"1.0"}' \
  localhost:8080/api/v1/users/me/onboarding
```

### 프론트(웹)에서 로컬 서버 부르기

`local` 프로필은 `localhost` 모든 포트의 브라우저 요청(CORS)을 허용합니다(`application-local.properties`). 폰 앱은 CORS와 상관없습니다.
배포 서버에서 웹 출처를 허용해야 하면 `cors.allowed-origins`에 쉼표로 적습니다. (기본: 허용 안 함)

### 시연용 시간 이동 (DEMO-01)

시연 서버는 `--spring.profiles.active=demo`로 실행합니다. (운영 프로필에는 이 API가 없습니다)

```bash
curl -H "Authorization: Bearer {토큰}" -H "Content-Type: application/json" \
  -d '{"days": 30}' localhost:8080/api/v1/demo/time-travel
# → { "serverNow": "2026-11-27T..." }  30일 지난 돌탑은 바로 완성(COMPLETED) 처리
```

- 서버 전체의 서비스 날짜가 함께 움직입니다. 로그인 토큰은 실제 시간 기준이라 만료되지 않습니다.
- 이동한 시간은 메모리에만 있어서 **서버를 다시 켜면 실제 시간으로 돌아옵니다.** (리허설 후 초기화할 때)

### 환경변수

| 이름 | 용도 | 없으면 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상, 배포 시 필수) | 로컬 개발용 기본값 |
| `GOOGLE_CLIENT_IDS` | 앱의 Google OAuth 클라이언트 ID (Android·iOS·웹은 쉼표로 구분) | Google 로그인 실패 |
| `EXPO_ACCESS_TOKEN` | Expo 푸시의 강화된 보안을 켰을 때만 | 인증 없이 발송 (기본) |
| `KAKAO_ADMIN_KEY` | 탈퇴 시 카카오 연결 끊기 (카카오 개발자 콘솔 어드민 키) | 연결 끊기만 건너뛰고 탈퇴는 진행 |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | 비밀번호 찾기 인증코드 메일 (예: Gmail은 `smtp.gmail.com`, `587`, 보내는 주소, 앱 비밀번호) | 메일 대신 서버 로그에 코드를 찍음 (로컬 개발용, 배포 시 필수) |
| `MAIL_FROM` | 보내는 사람 주소 | `SPRING_MAIL_USERNAME` |

Google은 서버에 Google 액세스 토큰이 없어서, 탈퇴 시 Google 연결 해제는 앱에서 합니다(`revokeAccess`).

확인: `curl localhost:8080/api/health`

```json
{ "success": true, "data": { "status": "UP" }, "error": null }
```

모든 API는 같은 형식(`success`, `data`, `error`)으로 응답합니다. 자세한 내용은 `docs/API명세서.md`를 참고하세요.

## DB 마이그레이션 (Flyway)

- `src/main/resources/db/migration/`
  - `V1__init.sql`: `docs/erd_final.sql`과 같은 내용 (전체 테이블)
  - `V2__seed_spots.sql`: 명소 시드 3곳
- 이미 적용된 파일은 **수정하지 마세요.** 테이블을 바꿀 때는 `V3__설명.sql`처럼 새 파일을 추가합니다.
- JPA는 `ddl-auto=validate`라서 엔티티와 테이블이 다르면 앱이 실행되지 않습니다.
- 로컬 DB를 처음부터 다시 만들고 싶으면: `dropdb hangajiman && createdb hangajiman`

## 폴더 구조

```text
src/main/java/com/hangajiman/
├── global/   공통 (응답 형식, 예외, BaseEntity, Clock·JPA 설정, security: JWT·온보딩 확인)
├── auth/     소셜 로그인(카카오·Google), Refresh Token, 탈퇴
├── user/     회원, 온보딩, 동의 이력, 내 정보
├── spot/     명소
├── notification/  알림함, Expo 푸시 발송 (알림은 NotificationService.notify()로만 만듭니다)
├── wish/     소원(돌탑)
├── stone/    돌 (오늘의 돌, 돌 목록)
└── (ai 추가 예정)
src/main/resources/
├── application.properties
└── db/migration/
```
