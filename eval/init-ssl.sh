#!/bin/bash
# ==========================================================================
# EES 최초 SSL 인증서 발급 및 Nginx HTTPS 전환 스크립트
# 사용법: ./init-ssl.sh your-email@example.com
# ==========================================================================
set -e

EMAIL=${1:?"사용법: ./init-ssl.sh your-email@example.com"}
DOMAIN="ees-eval.com"

echo "🔐 [1단계] Let's Encrypt SSL 인증서 최초 발급 시작..."
echo "📌 도메인: $DOMAIN, www.$DOMAIN"
echo "📌 이메일: $EMAIL"

# Certbot webroot 방식으로 인증서 발급
# Nginx의 80 포트 → /.well-known/acme-challenge/ 경로를 경유
docker compose -f docker-compose.prod.yml run --rm ees-certbot \
  certbot certonly --webroot \
  --webroot-path=/var/www/certbot \
  --email "$EMAIL" \
  --agree-tos \
  --no-eff-email \
  -d "$DOMAIN" \
  -d "www.$DOMAIN"

echo "✅ 인증서 발급 완료!"
echo ""

echo "🔄 [2단계] Nginx 설정을 SSL 포함 버전으로 교체..."
cp nginx/nginx-ssl.conf nginx/nginx.conf

echo "🔄 [3단계] Nginx 설정 구문 검증..."
docker exec ees-nginx nginx -t

echo "🔄 [4단계] Nginx 무중단 리로드..."
docker exec ees-nginx nginx -s reload

echo ""
echo "✅ [COMPLETE] https://$DOMAIN HTTPS 개통 완료!"
echo "🔒 브라우저에서 https://$DOMAIN 접속하여 자물쇠 아이콘을 확인하세요."
