# 서버 배포 (AWS EC2 + Docker)

EC2 한 대에 앱, PostgreSQL, Caddy(HTTPS)를 Docker로 함께 띄웁니다. 단디톤 시연용 `demo` 프로필(시간 이동 켜짐, 개발용 로그인 꺼짐)로 동작합니다.

## 1. 준비 (형님)
- **AWS 계정:** 결제 카드 등록, 결제 알림(예: 20달러) 설정
- **도메인:** 예 `api.hangajiman.com`. EC2를 만든 뒤 DNS에 A 레코드로 서버 IP를 연결합니다.
- **비밀값:** 아래 3단계 `.env`에 직접 넣습니다. 채팅에는 붙여 넣지 않습니다.

## 2. EC2 만들기
1. EC2 → 인스턴스 시작
   - 이미지: Ubuntu Server 24.04 LTS (64비트 Arm)
   - 유형: `t4g.small` (메모리 2GB)
   - 키 페어: 새로 만들고 `.pem` 파일 보관
   - 저장공간: 20GB
2. 보안 그룹 인바운드: `22`(SSH, 내 IP만), `80`, `443`(모두)
3. 탄력적 IP를 하나 받아 인스턴스에 연결 (재시작해도 IP가 바뀌지 않게)
4. 도메인 DNS에 A 레코드: `api` → 탄력적 IP

## 3. 서버에서 실행
```bash
ssh -i 키.pem ubuntu@탄력적IP

# Docker 설치
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu && exit   # 다시 ssh 접속

# 코드 받기
git clone https://github.com/han-gajiman/backend.git && cd backend/deploy
cp .env.example .env && nano .env        # 비밀값 채우기

# 실행 (처음 빌드는 몇 분 걸립니다)
docker compose up -d --build
docker compose logs -f app               # "Started HangajimanApplication"이 보이면 성공
```

확인: 브라우저에서 `https://도메인/api/v1/spots`가 명소 목록 JSON을 보여주면 됩니다.

## 4. 업데이트
```bash
cd ~/backend && git pull && cd deploy && docker compose up -d --build app
```

## 5. 알아 둘 점
- **DB:** 데이터는 Docker 볼륨 `db-data`에 남습니다. `docker compose down -v`는 데이터를 지우니 쓰지 마세요.
- **DB 마이그레이션:** 앱이 시작할 때 Flyway가 자동으로 적용합니다.
- **시간 이동:** `demo` 프로필이라 `POST /api/v1/demo/time-travel`이 켜져 있습니다. 서버 시계가 모든 회원에게 함께 바뀝니다.
- **메일:** `SPRING_MAIL_*`가 비어 있으면 비밀번호 찾기 코드가 메일 대신 로그에만 찍힙니다.
- **CORS:** 기본으로 `https://han-gajiman.expo.app`과 미리보기 주소(`https://han-gajiman--*.expo.app`)를 허용합니다.
