#!/bin/bash
set -e

echo "=================================================="
echo " 1. 2GB Swap Memory 활성화 (메모리 다운 방지)"
echo "=================================================="
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

echo "=================================================="
echo " 2. 시스템 업데이트 및 Docker 설치 & 자동 시작 설정"
echo "=================================================="
sudo apt-get update -y
sudo apt-get install -y docker.io docker-compose

sudo systemctl enable docker
sudo systemctl start docker

echo "=================================================="
echo " 3. Docker-compose 설정 (8080 포트 단독 구성)"
echo "=================================================="
mkdir -p ~/jenkins
cat << 'EOF' > ~/jenkins/docker-compose.yml
version: '3.8'

services:
  jenkins:
    image: jenkins/jenkins:lts-jdk21
    container_name: jenkins
    restart: unless-stopped
    user: root
    ports:
      - "8080:8080"
    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock
    environment:
      - TZ=Asia/Seoul

volumes:
  jenkins_home:
EOF

echo "=================================================="
echo " 4. 젠킨스 컨테이너 백그라운드 가동"
echo "=================================================="
cd ~/jenkins
sudo docker-compose up -d

echo "=================================================="
echo " 5. Jenkins 내부 Docker CLI 자동 주입"
echo "=================================================="
echo "젠킨스가 초기 준비를 마칠 때까지 15초간 대기합니다..."
sleep 15
sudo docker exec -u 0 jenkins apt-get update -y
sudo docker exec -u 0 jenkins apt-get install -y docker.io
echo "Jenkins 컨테이너 내부에 Docker CLI 설치가 정상 완료되었습니다."

echo "=================================================="
echo " 젠킨스 서버 완벽 자동 구축 완료!"
echo " http://[발급된_Azure_VM_공인IP]:8080 으로 접속하세요."
echo "=================================================="
