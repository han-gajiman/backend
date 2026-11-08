#!/usr/bin/env bash
# EC2를 처음 만들었을 때 한 번만 실행: Docker 설치, 재부팅 시 자동 시작, 메모리 부족 대비 스왑 2GB
# Amazon Linux 2023 / Ubuntu 22.04·24.04 지원
#   curl -fsSL https://raw.githubusercontent.com/han-gajiman/backend/main/deploy/ec2-setup.sh | bash
# (레포가 비공개면 레포를 먼저 클론한 뒤 bash deploy/ec2-setup.sh)
set -euo pipefail

if command -v dnf >/dev/null; then
  sudo dnf install -y docker git
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
elif command -v apt-get >/dev/null; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl git
  curl -fsSL https://get.docker.com | sudo sh
else
  echo "지원하지 않는 OS입니다. Amazon Linux 2023이나 Ubuntu를 써 주세요." >&2
  exit 1
fi

# 재부팅해도 Docker(와 restart: unless-stopped 컨테이너)가 자동으로 켜지게
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

# t3.micro(1GB)는 Gradle 빌드 중 메모리가 모자라서 스왑을 잡아 둡니다.
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo
echo "✅ 준비 끝. 한 번 로그아웃했다가 다시 접속한 뒤(docker 그룹 반영) deploy/README.md의 '첫 배포'를 진행하세요."
