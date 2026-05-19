#!/bin/bash
# ==========================================================================
# EES 배포 자동화: Ubuntu 내장 UFW 방화벽 활성화 및 포트 8080, 22 강제 개방 설정
# ==========================================================================
set -e

echo "🔒 [FIREWALL SETUP] UFW 기본 유입 차단 및 외부 방출 허용 설정..."
sudo ufw default deny incoming
sudo ufw default allow outgoing

echo "🔒 [FIREWALL SETUP] 원격 관리 포트 SSH (TCP 22) 인바운드 개방..."
sudo ufw allow 22/tcp

echo "🔒 [FIREWALL SETUP] 스프링 부트 웹 애플리케이션 서비스 포트 (TCP 8080) 인바운드 개방..."
sudo ufw allow 8080/tcp

echo "🔒 [FIREWALL SETUP] UFW 방화벽 활성화 처리..."
# 대화식 확인 입력을 우회하고 무인으로 실행되도록 --force 사용
sudo ufw --force enable

echo "✅ [FIREWALL SETUP] UFW 보안 정책 주입 및 구동이 완료되었습니다!"
sudo ufw status verbose
