# 한 가지만 — Backend (CLAUDE.md)

> 이 파일은 초안입니다. 🟡 표시는 팀 결정이 필요한 항목이니 임의로 정하지 말고 개발자에게 물어보세요.

## 서비스 개요
- 서비스명: 한 가지만 (2026 단디톤 출품작, 주제: RESONANCE — 우리가 대구·경북을 좋아하는 진짜 이유)
- 한 줄 설명: 소원 하나를 정하고, 30일 동안 매일 돌을 쌓으며 실천하고, 경북 명소를 방문해 이어가는 서비스
- 핵심 용어: 소원 = 돌탑 1개(30일) / 돌 = 하루 1개 기록 / 12개 돌탑 = 1년 회차
- 일정: 제출 11/20 23:59, 본선 11/21

## 기술 스택
Java 17 · Spring Boot 3.3.5 (모놀리식) · Gradle · Spring Data JPA · Spring Security + JWT · PostgreSQL · Spring Scheduler · LLM API · Expo Push · AWS EC2

## 기준 문서 (구현 전에 반드시 읽기)
- 기능명세서: `docs/기능명세서.md` (원본: Notion "기능명세서")
- ERD / DDL: `docs/erd_final.sql` (원본: Notion "ERD")
- API 명세서: `docs/API명세서.md` (원본: Notion "API 명세서")
- 기능명세서 다이어그램의 "Java 21"은 옛 정보입니다. Java는 17을 씁니다 (`build.gradle` 기준).
- 문서끼리 충돌하면 기능명세서 → ERD → 기술 스택 순으로 우선하고, 충돌 내용을 개발자에게 알리세요.
- 구버전 "MVP 범위 & 기능 명세" 페이지는 10/17 회의 이전 내용이라 참고하지 마세요.

## 패키지 구조 (도메인별)
```
com.hangajiman            (메인 클래스: HangajimanApplication)
├─ global/        공통 (응답 형식, 예외 처리, BaseEntity, 설정, Clock, 보안)
│   ├─ response/   ApiResponse(공통 응답), PageResponse(페이지네이션)
│   ├─ exception/  ErrorCode(API 명세서 에러 코드), BusinessException, GlobalExceptionHandler
│   ├─ config/     ClockConfig (KST Clock 빈)
│   └─ health/     GET /api/health
├─ auth/          소셜 로그인, 토큰 발급·재발급
├─ user/          회원, 온보딩, 약관·위치 동의, 탈퇴
├─ wish/          소원(돌탑), 회고, 모아보기
├─ stone/         돌 쌓기
├─ spot/          명소, 방문 인증, 디지털 엽서
├─ friend/        초대, 친구, 응원, 콕 찌르기, 익명 소원지기
├─ notification/  알림함, 푸시
└─ ai/            실천 목표 추천 (LLM 호출, 호출 횟수 기록)
```
- 도메인 안은 `controller / service / repository / domain(entity) / dto`로 나눕니다.
- 응답은 API 명세서 형식(`{success, data, error}`)을 따릅니다. 컨트롤러는 `ApiResponse.ok(...)`를 반환하고, 실패는 `throw new BusinessException(ErrorCode.XXX)`로 처리합니다. 새 에러 코드는 API 명세서와 `ErrorCode`에 같이 추가하세요.
- base 패키지는 `com.hangajiman`입니다. (결정됨 10/23)
- 🟡 도메인 간 호출 규칙(서비스끼리 직접 호출 vs 이벤트)은 미정입니다.

## 반드시 지킬 규칙
1. **시간**: `LocalDate.now()`, `Instant.now()`를 직접 쓰지 마세요. 주입된 `Clock`을 쓰세요. 시연용 시간 가속(DEMO-01)이 이 Clock을 바꾸는 방식이라, 직접 호출하면 시연이 깨집니다. 날짜 기준은 KST입니다.
2. **소원 수정 불가**: `wishes.content`, `wishes.action_goal`을 바꾸는 API를 만들지 마세요. 취소도 없습니다.
3. **하루 1개**: 돌은 `UNIQUE(wish_id, stone_date)`로 보장하고, 서비스에서도 검증하세요. 돌 개수는 `COUNT(*)`로 셉니다.
4. **위치정보**: 사용자 좌표는 거리 계산에만 쓰고 **저장하지 않습니다**(`spot_visits.distance_m`만 저장). `LOCATION` 동의가 없으면 방문 인증을 거부하세요.
5. **인증 토큰**: Refresh Token은 해시만 저장하고, 재발급 때마다 교체합니다. 로그아웃과 탈퇴 시 삭제하세요.
6. **회원 탈퇴**: 소셜 연결 끊기 → `DELETE FROM users`로 CASCADE 정리. `notifications.target_id`는 FK가 없으니 서비스 레이어에서 같이 정리하세요.
7. **LLM**: 호출은 백엔드에서만 하고 API 키를 클라이언트에 노출하지 마세요. 타임아웃 5초, 1인 하루 10회, 실패하면 기본 추천 문구로 대체합니다. 소원 내용은 저장하지 않고 호출 시각만 `ai_suggestion_logs`에 남깁니다.
8. **스키마 변경 금지**: ERD(`erd_final.sql`)에 없는 테이블·컬럼은 임의로 만들지 말고 먼저 물어보세요.
   - 테이블은 Flyway(`src/main/resources/db/migration/`)로만 만듭니다. `V1__init.sql`은 `docs/erd_final.sql`과 같은 내용입니다.
   - 이미 적용된 마이그레이션 파일은 수정하지 말고, 변경은 새 파일(`V3__설명.sql`)로 추가하세요. ERD 문서도 같이 고쳐야 합니다.
   - JPA는 `ddl-auto=validate`입니다. 엔티티는 `BaseEntity`를 상속하고, 컬럼 타입·길이를 ERD와 맞추세요.

## 미결정 항목 🟡 — 임의 결정 금지
- 다짐 기간 2주의 의미 (수정 가능 기간이면 `wishes.editable_until` 추가)
- 진행 중 돌탑 개수 (현재는 1인 1개, 부분 유니크 인덱스 `uq_wishes_one_open`)
- 익명 소원지기 매칭 방식 (현재 구조는 자동 매칭 가정)
- 명소 카테고리 기준
- 방문 인증·디지털 엽서의 P0 승격 여부, Apple 로그인 범위, 소원 등록 전/성취 후 방문 흐름
- 브랜치·커밋 규칙

## 구현 순서
- 우선순위는 기능명세서의 P0 → P1 → P2 순서입니다.
- 작업 단위는 기능 ID(예: AUTH-01)이고, PR 하나에 기능 ID 하나를 권장합니다.
- 큰 작업은 Plan 모드로 계획을 먼저 보여주세요.
- 시연 경로(로그인 → 소원 등록 → 돌 쌓기 → 시간 가속 → 완성 회고 → 모아보기)가 끝까지 이어지는 것을 최우선으로 합니다.
- 시연용 기능(DEMO-01 시간 가속, DEMO-02 위치 인증 우회)은 시연 프로필에서만 켜지게 하세요.

## 작업 방식
- 코드를 쓰기 전에 관련 문서를 읽고, 이해한 내용과 계획을 먼저 짧게 알려주세요.
- 기능 하나를 만들 때 정상 케이스와 실패 케이스를 확인할 수 있는 방법(Postman 시나리오 등)을 함께 제시하세요.
