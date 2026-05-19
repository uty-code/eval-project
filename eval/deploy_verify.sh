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

echo "🔍 [검증 2단계] 포트 8080 연결 대기 및 응답성 테스트..."
# 스프링 부트 톰캣 서버가 완전히 뜰 때까지 최대 30초 대기하며 검사
for i in {1..10}
do
  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/internal-monitor/health || true)
  if [ "$HTTP_STATUS" -eq 200 ] || [ "$HTTP_STATUS" -eq 302 ]; then
    echo "✅ [SUCCESS] http://localhost:8080/internal-monitor/health 응답성 확인 완료 (HTTP Code: $HTTP_STATUS)"
    break
  fi
  echo "⏳ 애플리케이션 초기화 대기 중... (${i}/10)"
  sleep 3
done

if [ "$HTTP_STATUS" -ne 200 ] && [ "$HTTP_STATUS" -ne 302 ]; then
  echo "❌ [ERROR] 애플리케이션 초기화 및 포트 8080 헬스체크에 실패했습니다!"
  echo "📊 최근 컨테이너 로그 출력:"
  docker logs --tail 30 ees-eval-app
  exit 1
fi

echo "✅ [COMPLETE] 모든 배포 검증 단계가 성공적으로 완수되었습니다!"
