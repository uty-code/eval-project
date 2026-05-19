#!/bin/bash
# ==========================================================================
# EES 배포 자동화: 신규 배포 컨테이너 기동 상태 및 네트워크 헬스체크 검증 스크립트
# ==========================================================================
set -e

echo "🔍 [검증 1단계] 도커 컨테이너 동작 상태 정밀 진단..."
# ees-eval-app 컨테이너 실행 여부 체크
APP_STATUS=$(docker ps -q --filter "name=ees-eval-app" --filter "status=running" | wc -l)
if [ "$APP_STATUS" -eq 0 ]; then
  echo "❌ [ERROR] ees-eval-app 컨테이너가 정상적으로 구동되고 있지 않습니다!"
  exit 1
fi

# ees-nginx 컨테이너 실행 여부 체크
NGINX_STATUS=$(docker ps -q --filter "name=ees-nginx" --filter "status=running" | wc -l)
if [ "$NGINX_STATUS" -eq 0 ]; then
  echo "❌ [ERROR] ees-nginx 컨테이너가 작동 중이 아닙니다!"
  exit 1
fi
echo "✅ [SUCCESS] 도커 컨테이너 기동 확인 완료."

echo "🔍 [검증 2단계] Nginx HTTP(80) -> HTTPS(443) 강제 리다이렉션 보안 설정 검증..."
HTTP_REDIRECT_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/)
if [ "$HTTP_REDIRECT_CODE" -eq 301 ] || [ "$HTTP_REDIRECT_CODE" -eq 302 ]; then
  echo "✅ [SUCCESS] HTTP -> HTTPS 보안 강제 리다이렉트 정상 작동 중 (HTTP Code: $HTTP_REDIRECT_CODE)"
else
  echo "❌ [ERROR] Nginx 리다이렉트 보안 검증 실패! (수신된 코드: $HTTP_REDIRECT_CODE, 301/302 기대됨)"
  exit 1
fi

echo "🔍 [검증 3단계] Nginx HTTPS(443) 보안 터널 경유 스프링 헬스체크 및 실 데이터 정밀 검증..."
# Nginx와 스프링 부트가 완전히 웜업될 때까지 루프 수행
SUCCESS_VERIFIED=false
for i in {1..15}
do
  # Host 헤더에 도메인을 정밀 이식하여 Nginx SSL 가상호스트 매칭을 유도
  # 단일 요청으로 바디와 HTTP 상태 코드를 동시에 원자적으로 획득 (타이밍 불일치 방지)
  RESPONSE=$(curl -k -s -w "\n%{http_code}" -H "Host: ees-eval.com" https://localhost/internal-monitor/health || true)
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n1)
  RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')
  
  echo "📡 헬스체크 수신 데이터 확인 중... - 응답 코드: $HTTP_STATUS"
  
  # 1. HTTP 200 성공 여부 체크
  # 2. JSON 바디 내부의 실질적인 서비스 정상 가동 상태("status":"UP") 여부 동시 정밀 파싱
  if [ "$HTTP_STATUS" -eq 200 ] && echo "$RESPONSE_BODY" | grep -q '"status":"UP"'; then
    echo "✅ [SUCCESS] Nginx HTTPS 관문 통과 및 내부 헬스체크 최종 검증 성공!"
    echo "📊 수신 데이터: $RESPONSE_BODY"
    SUCCESS_VERIFIED=true
    break
  fi
  
  echo "⏳ 스프링 부트 기동 및 데이터베이스 커넥션 웜업 대기 중... (${i}/15)"
  sleep 3
done

if [ "$SUCCESS_VERIFIED" = false ]; then
  echo "❌ [ERROR] Nginx HTTPS 경유 최종 헬스체크 및 JSON 데이터 검증에 실패했습니다!"
  echo "📊 수신된 최종 응답 바디: $RESPONSE_BODY"
  echo "📊 최근 컨테이너 로그 출력:"
  docker logs --tail 30 ees-eval-app
  docker logs --tail 15 ees-nginx
  exit 1
fi

echo "🎉 [COMPLETE] Nginx 보안 레이어 및 스프링 백엔드가 완벽하게 결합되어 가동 중임을 공식 검증 완료했습니다!"

