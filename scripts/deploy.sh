#!/usr/bin/env bash
# GHCR 이미지를 받아 전체 서비스를 띄우고 Gateway 헬스 체크 4개를 확인합니다.
# 사용법 (저장소 루트에서):
#   ./scripts/deploy.sh                     develop 태그 이미지로 배포
#   IMAGE_TAG=<커밋SHA> ./scripts/deploy.sh   특정 버전으로 배포
#   SKIP_PULL=1 ./scripts/deploy.sh         pull 없이 로컬에 있는 이미지로 확인
set -euo pipefail

cd "$(dirname "$0")/.."

compose() {
  docker compose -f compose.yaml -f compose.deploy.yaml "$@"
}

if [ "${SKIP_PULL:-0}" != "1" ]; then
  echo "[1/3] 이미지 받기 (태그: ${IMAGE_TAG:-develop})"
  compose pull
else
  echo "[1/3] 이미지 받기 건너뜀 (SKIP_PULL=1)"
fi

echo "[2/3] 서비스 기동 (최대 10분 대기)"
compose up -d --no-build --wait --wait-timeout 600

echo "[3/3] Gateway 헬스 체크"
# 실행 중인 Gateway의 JWT 키로 10분짜리 테스트 토큰을 만듭니다. 키와 토큰은 출력하지 않습니다.
secret=$(compose exec -T gateway-service printenv JWT_SECRET)
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"1","role":"USER","iat":%d,"exp":%d}' "$now" "$((now + 600))" | b64url)
signature=$(printf '%s' "$header.$payload" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)
token="$header.$payload.$signature"

# Eureka 등록에 시간이 걸리므로 서비스마다 5초 간격으로 최대 24번(2분) 다시 시도합니다.
failed=0
for service in user policy engagement recommendation; do
  passed=0
  for _ in $(seq 1 24); do
    body=$(compose exec -T gateway-service curl --silent --max-time 5 \
      -H "Authorization: Bearer $token" \
      "http://localhost:8000/_infra/health/$service" || true)
    if [ "$body" = "$service-service OK" ]; then
      passed=1
      break
    fi
    sleep 5
  done

  if [ "$passed" = 1 ]; then
    echo "PASS: Gateway -> $service-service"
  else
    echo "FAIL: Gateway -> $service-service (마지막 응답: ${body:-없음})"
    failed=1
  fi
done

if [ "$failed" = 1 ]; then
  echo "배포 확인 실패. 로그 확인: docker compose logs --tail 100 <서비스이름>"
  exit 1
fi
echo "배포 완료: 서비스 4개 모두 정상입니다."
