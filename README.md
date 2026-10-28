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
| - | `GET /api/v1/dev/token?userId=` 개발용 토큰 (`local` 프로필 전용) | 불필요 | ✅ |

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

### 환경변수

| 이름 | 용도 | 없으면 |
| --- | --- | --- |
| `JWT_SECRET` | JWT 서명 키 (32바이트 이상, 배포 시 필수) | 로컬 개발용 기본값 |
| `GOOGLE_CLIENT_IDS` | 앱의 Google OAuth 클라이언트 ID (Android·iOS·웹은 쉼표로 구분) | Google 로그인 실패 |
| `KAKAO_ADMIN_KEY` | 탈퇴 시 카카오 연결 끊기 (카카오 개발자 콘솔 어드민 키) | 연결 끊기만 건너뛰고 탈퇴는 진행 |

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
├── wish/     소원(돌탑)
├── stone/    돌 (오늘의 돌, 돌 목록)
└── (friend, notification, ai 순서대로 추가 예정)
src/main/resources/
├── application.properties
└── db/migration/
```
