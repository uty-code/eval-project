#!/bin/bash
# ==========================================================================
# EES 배포 자동화: 배포 장애 상황 대비 신속한 이전 버전(백업) 원클릭 복구 스크립트
# ==========================================================================
set -e

echo "⚠️ [ROLLBACK START] 즉각적인 서비스 복구를 위한 이전 컨테이너 복원 실행..."

# 백업용으로 남겨둔 컨테이너가 존재하는지 확인
BACKUP_EXISTS=$(docker ps -a -q --filter "name=ees-app-backup" | wc -l)

if [ "$BACKUP_EXISTS" -eq 0 ]; then
  echo "❌ [ERROR] 복구할 ees-app-backup 컨테이너가 서버 내에 존재하지 않습니다!"
  exit 1
fi

echo "🔄 [1단계] 현재 장애가 발생한 새 컨테이너 정지 및 파기..."
docker stop ees-eval-app || true
docker rm ees-eval-app || true

echo "🔄 [2단계] ees-app-backup 컨테이너를 ees-eval-app으로 원상 복구 및 재시작..."
docker rename ees-app-backup ees-eval-app
docker start ees-eval-app

echo "🔍 [3단계] 롤백된 컨테이너의 헬스체크 검증..."
CONTAINER_STATUS=$(docker ps -q --filter "name=ees-eval-app" --filter "status=running" | wc -l)

if [ "$CONTAINER_STATUS" -eq 1 ]; then
  echo "✅ [ROLLBACK COMPLETE] 이전 버전으로의 롤백 및 서비스 재가동이 완벽히 성공했습니다!"
else
  echo "❌ [FATAL] 롤백 복구 컨테이너 재가동에 실패했습니다. 수동 확인이 필요합니다!"
  exit 1
fi
