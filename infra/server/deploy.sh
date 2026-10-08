#!/usr/bin/env bash
# 배포 전용 SSH 키의 강제 명령(authorized_keys command=)으로만 실행된다.
#   허용 명령: "deploy <40자리 git SHA>"
#   동작: 이미지 pull → compose up → 헬스체크 → 실패 시 직전 SHA로 롤백
set -euo pipefail

APP_DIR=/opt/sqlp
COMPOSE=(docker compose -f "$APP_DIR/compose.prod.yml" --env-file "$APP_DIR/.env")
HEALTH_URL=http://127.0.0.1:8000/api/system/info

log() { echo "[deploy] $*"; }

read -r -a args <<< "${SSH_ORIGINAL_COMMAND:-}"
if [[ ${#args[@]} -ne 2 || ${args[0]} != deploy || ! ${args[1]} =~ ^[0-9a-f]{40}$ ]]; then
	log "거부: 허용되지 않은 명령"
	exit 2
fi
sha=${args[1]}

exec 9> "$APP_DIR/.deploy.lock"
flock -n 9 || { log "다른 배포가 진행 중"; exit 3; }

up() {
	IMAGE_TAG=$1 "${COMPOSE[@]}" pull --quiet api web
	IMAGE_TAG=$1 "${COMPOSE[@]}" up -d --remove-orphans
}

healthy() {
	for _ in $(seq 1 30); do
		curl -fsS --max-time 3 "$HEALTH_URL" > /dev/null 2>&1 && return 0
		sleep 3
	done
	return 1
}

previous=$(cat "$APP_DIR/.current_sha" 2>/dev/null || true)

log "배포 시작: $sha (이전: ${previous:-없음})"
up "$sha"

if healthy; then
	echo "$sha" > "$APP_DIR/.current_sha"
	docker image prune -af --filter "until=168h" > /dev/null
	log "성공: $sha"
	exit 0
fi

log "헬스체크 실패"
IMAGE_TAG=$sha "${COMPOSE[@]}" logs --tail 50 api web || true
if [[ -n $previous ]]; then
	log "롤백: $previous"
	up "$previous"
	if healthy; then log "롤백 완료"; else log "롤백 후에도 비정상"; fi
fi
exit 1
