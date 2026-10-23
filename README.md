# backend

Spring Boot 3.3.5 기반 한 가지만 백엔드 프로젝트입니다.

## 프로젝트 개요

- Java 17
- Spring Boot 3.3.5
- Gradle 9.1.0
- 기본 API: `/api/health`

## 실행 방법

```bash
./gradlew bootRun
```

테스트 실행:

```bash
./gradlew test
```

빌드 실행:

```bash
./gradlew build
```

## 기본 엔드포인트

- `GET /api/health`

응답 예시:

```json
{
  "success": true,
  "data": { "status": "UP" },
  "error": null
}
```

모든 API는 같은 형식(`success`, `data`, `error`)으로 응답합니다. 자세한 내용은 `docs/API명세서.md`를 참고하세요.

## 포트

- 기본 포트: `8080`

## 폴더 구조

```text
backend/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/
├── src/
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
└── README.md
```
