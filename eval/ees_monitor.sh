#!/bin/bash

# .env 파일에서 환경변수 로드
if [ -f .env ]; then
  # export 명령어가 없어도 변수를 사용할 수 있도록 allexport 설정
  set -a
  source .env
  set +a
fi

# 환경변수에서 디스코드 웹훅 URL 가져오기
WEBHOOK_URL="${DISCORD_MONITOR_WEBHOOK}"

if [ -z "$WEBHOOK_URL" ]; then
  echo "⚠️ [WARNING] .env 파일에 DISCORD_MONITOR_WEBHOOK이 설정되어 있지 않습니다. 알림을 생략합니다."
  exit 0
fi

# 임계치 설정 (%)
CPU_THRESHOLD=80
MEM_THRESHOLD=85
DISK_THRESHOLD=90

# 메트릭 수집
# CPU는 사용자 + 시스템 사용량 합산
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2 + $4}' | cut -d. -f1)
MEM_USAGE=$(free | awk '/Mem/ {printf("%.0f"), $3/$2 * 100.0}')
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')

# Spring Boot 컨테이너 헬스체크 (다운 감지)
# 8080 포트가 외부로 열려있지 않으므로 docker exec를 사용하여 컨테이너 내부에서 확인
HTTP_STATUS=$(docker exec ees-eval-app curl -s -m 5 -o /dev/null -w "%{http_code}" http://localhost:8080/internal-monitor/health || echo "000")

ALERTS=""
COLOR=15158332 # 기본 빨간색

# 자원 경고 체크
if [ "$CPU_USAGE" -ge "$CPU_THRESHOLD" ]; then
    ALERTS="$ALERTS\n- ⚠️ **CPU 사용량 경고**: 현재 ${CPU_USAGE}% (임계치: ${CPU_THRESHOLD}%)"
fi

if [ "$MEM_USAGE" -ge "$MEM_THRESHOLD" ]; then
    ALERTS="$ALERTS\n- ⚠️ **메모리 사용량 경고**: 현재 ${MEM_USAGE}% (임계치: ${MEM_THRESHOLD}%)"
fi

if [ "$DISK_USAGE" -ge "$DISK_THRESHOLD" ]; then
    ALERTS="$ALERTS\n- ⚠️ **디스크 사용량 경고**: 현재 ${DISK_USAGE}% (임계치: ${DISK_THRESHOLD}%)"
fi

# 앱 다운(장애) 체크
if [ "$HTTP_STATUS" != "200" ]; then
    if [ -f "/home/ubuntu/ees-app/.deploying" ]; then
        # 파일이 5분 이상 지났는지 체크 (가비지 파일 방지)
        if test "`find /home/ubuntu/ees-app/.deploying -mmin +5`"; then
            rm -f /home/ubuntu/ees-app/.deploying
            ALERTS="$ALERTS\n\n- 🚨 **서비스 장애 감지 (DOWN)**: Spring Boot 애플리케이션이 응답하지 않습니다! (상태 코드: ${HTTP_STATUS})"
        else
            ALERTS="$ALERTS\n\n- ℹ️ **배포 진행 중**: 애플리케이션 응답이 없으나 배포 중이므로 알람을 생략합니다."
            # 배포 중이므로 알람 색상을 빨간색에서 노란색 계열로 변경
            COLOR=16776960
        fi
    else
        ALERTS="$ALERTS\n\n- 🚨 **서비스 장애 감지 (DOWN)**: Spring Boot 애플리케이션이 응답하지 않습니다! (상태 코드: ${HTTP_STATUS})"
    fi
fi

# 알림 보낼 내용이 있으면 Discord로 전송
if [ -n "$ALERTS" ]; then
    PAYLOAD=$(cat <<EOF
{
  "embeds": [
    {
      "title": "🔥 KT Cloud 운영 서버 긴급 경고",
      "description": "운영 서버에서 다음과 같은 이상이 감지되었습니다.\n$ALERTS",
      "color": $COLOR,
      "fields": [
        {"name": "서버 IP", "value": "210.104.76.134", "inline": true},
        {"name": "조치 가이드", "value": "SSH로 접속하여 컨테이너 상태(docker ps) 및 자원(htop)을 확인하세요.", "inline": false}
      ]
    }
  ]
}
EOF
)
    curl -s -X POST -H "Content-Type: application/json" -d "$PAYLOAD" "$WEBHOOK_URL"
fi
