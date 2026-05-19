#!/bin/bash
# ==========================================================================
# EES 배포 자동화: 배포 실패 시 백업 버전으로 복구하는 스크립트
# ==========================================================================
set -e

echo "⚠️ [ROLLBACK START] 이전 이미지 기반 복구 시작..."

# 1. .env 파일에서 복구 대상인 이전 정상 태그(BACKUP_TAG) 정보 추출
if [ -f .env ]; then
  BACKUP_TAG=$(grep -E "^BACKUP_TAG=" .env | cut -d'=' -f2 || echo "")
else
  echo "❌ [ERROR] .env 설정 파일이 존재하지 않아 백업 태그를 식별할 수 없습니다!"
  exit 1
fi

if [ -z "$BACKUP_TAG" ]; then
  echo "⚠️ [WARNING] BACKUP_TAG가 명시되어 있지 않아 'latest' 버전을 폴백 백업으로 적용합니다."
  BACKUP_TAG="latest"
fi

echo "🔄 [1단계] 현재 기동에 실패한 컨테이너 파기..."
docker compose --compatibility -f docker-compose.prod.yml down || true

echo "🔄 [2단계] .env 파일의 활성 TAG를 이전 정상 작동 TAG($BACKUP_TAG)로 덮어쓰기 복원..."
# OS 이식성을 고려해 안전하게 임시파일 교체 방식으로 sed 수행
sed "s/^TAG=.*/TAG=${BACKUP_TAG}/g" .env > .env.tmp && mv .env.tmp .env

echo "🔄 [3단계] 복구 버전($BACKUP_TAG) 이미지로 컨테이너 재기동..."
docker compose --compatibility -f docker-compose.prod.yml up -d

echo "🔍 [4단계] 롤백 복원된 컨테이너 상태 최종 검증..."
# deploy_verify.sh의 검증 로직 호출 (롤백 후 상태 헬스체크 수행)
./deploy_verify.sh

echo "✅ [ROLLBACK COMPLETE] 이전 안정화 버전($BACKUP_TAG)으로 복구가 완료되었습니다."
