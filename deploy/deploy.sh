#!/usr/bin/env bash
# 새 코드 반영: main을 받아 다시 빌드하고, 서버가 정상 응답할 때까지 확인합니다.
#   cd ~/backend && bash deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo ".env가 없습니다. cp .env.example .env 후 값을 채워 주세요." >&2
  exit 1
fi

git pull --ff-only origin main
docker compose up -d --build
docker image prune -f >/dev/null

echo "서버 기동 확인 중 (최대 3분)..."
for _ in $(seq 1 36); do
  if curl -fsS http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "✅ 배포 완료: $(curl -fsS http://localhost:8080/api/health)"
    docker compose ps
    exit 0
  fi
  sleep 5
done

echo "❌ 3분 안에 서버가 응답하지 않았습니다. 로그를 확인하세요: docker compose logs --tail=100 app" >&2
exit 1
