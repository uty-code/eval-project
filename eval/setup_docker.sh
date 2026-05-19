#!/bin/bash
# ==========================================================================
# EES 배포 자동화: Ubuntu OS 전용 Docker Engine 및 Compose 공식 무인 설치 스크립트
# ==========================================================================
set -e

echo "🐳 [DOCKER SETUP] Ubuntu 시스템 패키지 업데이트 및 빌드 필수 도구 설치..."
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

echo "🐳 [DOCKER SETUP] Docker 공식 GPG 키 등록 및 APT 저장소 추가..."
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg --yes

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

echo "🐳 [DOCKER SETUP] Docker Engine 및 최신 Compose 플러그인 동시 설치..."
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "🐳 [DOCKER SETUP] ubuntu 사용자 계정에 sudo-free Docker 실행 그룹 권한 부여..."
sudo usermod -aG docker ubuntu

echo "✅ [DOCKER SETUP] Docker 및 Compose 인프라 설치 작업이 완료되었습니다!"
echo "⚠️ [IMPORTANT] 권한 적용을 위해 반드시 현재 SSH 세션을 종료(로그아웃)하고 다시 재접속해 주십시오."
