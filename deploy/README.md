# EC2 배포

Docker Compose로 PostgreSQL · 백엔드 · Caddy(HTTPS) · autoheal을 한 번에 띄웁니다.

| 서비스 | 하는 일 |
| --- | --- |
| `db` | PostgreSQL 16. 데이터는 `db-data` 볼륨에 남아서 컨테이너를 다시 만들어도 유지 |
| `app` | 백엔드 (Spring Boot). 테이블은 기동할 때 Flyway가 만듦 |
| `caddy` | 80·443 포트. `DOMAIN`이 있으면 HTTPS 인증서를 자동으로 받음 |
| `autoheal` | 백엔드가 살아 있는데 응답이 없으면(헬스체크 실패) 재시작 |

## 서버가 다운되면

| 상황 | 복구 |
| --- | --- |
| 백엔드 프로세스가 죽음 (에러, 메모리 부족) | `restart: unless-stopped`로 바로 다시 실행 |
| 살아 있는데 응답 없음 | `/api/health`를 30초마다 확인, 3번 실패하면 autoheal이 재시작 |
| EC2 재부팅 | Docker가 부팅 때 켜지면서 컨테이너 전부 다시 실행 |

## 처음 한 번

1. **EC2 만들기**: Amazon Linux 2023 또는 Ubuntu, t3.small 권장 (t3.micro도 되지만 빌드가 느림)
   - 보안 그룹 인바운드: 22(SSH, 내 IP만), 80, 443
   - 탄력적 IP를 붙이면 재시작해도 주소가 안 바뀜
2. **접속해서 설치**
   ```bash
   ssh -i 키.pem ec2-user@<퍼블릭 IP>          # Ubuntu면 ubuntu@
   git clone https://github.com/han-gajiman/backend.git
   cd backend && bash deploy/ec2-setup.sh
   exit                                          # docker 그룹 반영을 위해 다시 접속
   ```
   비공개 레포라 클론할 때 GitHub 아이디와 Personal Access Token(비밀번호 자리)이 필요합니다.

## 첫 배포

```bash
cd ~/backend
cp .env.example .env
openssl rand -base64 32        # 나온 값을 JWT_SECRET에
nano .env                      # DOMAIN, DB_PASSWORD, JWT_SECRET 필수
bash deploy/deploy.sh
```

- `DOMAIN`: 퍼블릭 IP가 `3.35.1.2`면 `3-35-1-2.sslip.io`. 도메인을 사지 않아도 HTTPS가 됩니다.
- 확인: `https://<DOMAIN>/api/health` → `{"success":true,"data":{"status":"UP"},...}`
- 앱 `.env.local`: `EXPO_PUBLIC_API_URL=https://<DOMAIN>/api/v1`, `EXPO_PUBLIC_USE_MOCK=false`

## 코드 업데이트

```bash
cd ~/backend && bash deploy/deploy.sh   # main 받아서 다시 빌드·재시작·헬스체크
```

## 자주 쓰는 명령

```bash
docker compose ps                      # 상태 (app이 healthy인지)
docker compose logs -f --tail=100 app  # 백엔드 로그
docker compose restart app             # 백엔드만 재시작
docker compose down                    # 전부 중지 (DB 데이터는 남음)
docker compose exec db psql -U hangajiman hangajiman   # DB 접속
```

## 주의

- `.env`는 git에 올리지 마세요. (`.gitignore`에 있음)
- 처음 띄운 뒤 `DB_PASSWORD`를 바꾸면 기존 DB에 접속이 안 됩니다. 바꾸려면 `docker compose down -v`로 DB까지 지우고 다시 띄워야 합니다(데이터 삭제).
- `SPRING_PROFILES_ACTIVE=demo`면 시연용 시간 이동 API(`/api/v1/demo/time-travel`)가 켜집니다.
