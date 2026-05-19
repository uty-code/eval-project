#!/bin/bash
# ==========================================================================
# EES 배포 자동화: 신규 배포 컨테이너 기동 상태 및 네트워크 헬스체크 검증 스크립트
# ==========================================================================
set -e

echo "🔍 [검증 1단계] 'ees-eval-app' 도커 컨테이너 구동 상태 조회..."
docker ps --filter "name=ees-eval-app"

# 컨테이너가 정상적으로 실행 중인지 프로세스 수 확인
CONTAINER_STATUS=$(docker ps -q --filter "name=ees-eval-app" --filter "status=running" | wc -l)

if [ "$CONTAINER_STATUS" -eq 0 ]; then
  echo "❌ [ERROR] ees-eval-app 컨테이너가 정상적으로 구동되고 있지 않습니다!"
  exit 1
fi

echo "🔍 [검증 2단계] Nginx 리버스 프록시(HTTPS 443) 경유 헬스체크 응답성 테스트..."
# Nginx의 HTTPS(443) 보안 포트와 리버스 프록시 동작을 유저의 관점 그대로 교차 검증 (-k 로컬 인증서 경고 무시)
for i in {1..10}
do
  HTTP_STATUS=$(curl -k -s -o /dev/null -w "%{http_code}" https://localhost/internal-monitor/health || true)
  if [ "$HTTP_STATUS" -eq 200 ]; then
    echo "✅ [SUCCESS] https://localhost/internal-monitor/health 응답성 확인 완료 (HTTP Code: $HTTP_STATUS)"
    break
  fi
  echo "⏳ 애플리케이션 초기화 대기 중... (${i}/10)"
  sleep 3
done

if [ "$HTTP_STATUS" -ne 200 ]; then
  echo "❌ [ERROR] Nginx 리버스 프록시 경유 헬스체크에 실패했습니다!"
  echo "📊 최근 컨테이너 로그 출력:"
  docker logs --tail 30 ees-eval-app
  docker logs --tail 10 ees-nginx
  exit 1
fi

echo "✅ [COMPLETE] 모든 배포 검증 단계가 성공적으로 완수되었습니다!"
