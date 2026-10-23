# backend

한 가지만(2026 단디톤) 백엔드입니다.

- 개발 규칙: `CLAUDE.md`
- 기능·API·ERD 기준 문서: `docs/`

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
```

## 실행

```bash
./gradlew bootRun                                  # http://localhost:8080
./gradlew bootRun --args='--server.port=8081'      # 8080을 다른 앱이 쓰고 있을 때
./gradlew test                                     # 테스트 (hangajiman_test DB 사용)
```

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
├── global/   공통 (응답 형식, 예외, BaseEntity, Clock·JPA 설정)
├── spot/     명소
└── (auth, user, wish, stone, friend, notification, ai 순서대로 추가 예정)
src/main/resources/
├── application.properties
└── db/migration/
```
