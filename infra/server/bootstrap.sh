#!/usr/bin/env bash
# OCI Ampere A1 (Ubuntu 24.04 aarch64) 서버 최초 설정. root로 한 번 실행한다.
#   sudo bash bootstrap.sh "<배포 공개키 한 줄>"
# 하는 일: 보안 업데이트 자동화, Docker, Tailscale, 방화벽, deploy 계정, 배포 스크립트, 일일 DB 백업
set -euo pipefail

DEPLOY_PUBKEY=${1:?배포용 SSH 공개키(ssh-ed25519 ...)를 인자로 넘긴다}
APP_DIR=/opt/sqlp
SRC_DIR=$(cd "$(dirname "$0")" && pwd)

echo "== 패키지 업데이트, 자동 보안 패치"
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get -y upgrade
DEBIAN_FRONTEND=noninteractive apt-get -y install ca-certificates curl ufw unattended-upgrades fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== 방화벽: 인바운드 전부 차단, Tailscale 인터페이스의 SSH만 허용"
# OCI Ubuntu 이미지는 netfilter-persistent로 iptables 규칙(INPUT 끝에 REJECT)을 넣어 둔다.
# ufw 규칙이 그 뒤에 붙으면 동작하지 않으므로 ufw로 일원화한다. Docker 설치 전에 해야 Docker 체인을 건드리지 않는다.
if systemctl is-enabled --quiet netfilter-persistent 2>/dev/null; then
	systemctl disable --now netfilter-persistent
	[[ -f /etc/iptables/rules.v4 ]] && mv /etc/iptables/rules.v4 /etc/iptables/rules.v4.oci-default
	iptables -F INPUT
fi
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow in on tailscale0 to any port 22 proto tcp
# 최초 설정 중 끊기지 않도록 공인 SSH는 Tailscale 확인 후 직접 닫는다(docs/deploy.md 참고)
ufw allow 22/tcp comment 'temporary: remove after tailscale ssh works'
ufw --force enable

echo "== Docker Engine"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# shellcheck source=/dev/null
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
	> /etc/apt/sources.list.d/docker.list
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get -y install docker-ce docker-ce-cli containerd.io docker-compose-plugin

echo "== Tailscale"
curl -fsSL https://tailscale.com/install.sh | sh

echo "== SSH 강화"
cat > /etc/ssh/sshd_config.d/60-sqlp.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
AllowTcpForwarding no
X11Forwarding no
EOF
systemctl reload ssh

echo "== deploy 계정과 배포 스크립트"
id deploy > /dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
# docker 그룹은 사실상 root 권한이다. 대신 deploy 키는 deploy.sh 실행만 가능하도록 강제 명령으로 묶는다.
usermod -aG docker deploy
install -d -o deploy -g deploy -m 750 "$APP_DIR" "$APP_DIR/backups"
install -o root -g root -m 755 "$SRC_DIR/deploy.sh" "$APP_DIR/deploy.sh"
install -o root -g deploy -m 640 "$SRC_DIR/../compose.prod.yml" "$APP_DIR/compose.prod.yml"
install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
echo "command=\"$APP_DIR/deploy.sh\",restrict $DEPLOY_PUBKEY" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 600 /home/deploy/.ssh/authorized_keys

if [[ ! -f $APP_DIR/.env ]]; then
	cat > "$APP_DIR/.env" <<EOF
IMAGE_OWNER=CHANGE_ME_lowercase_github_owner
POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -d '/+=')
EOF
	chown root:deploy "$APP_DIR/.env"
	chmod 640 "$APP_DIR/.env"
fi

echo "== 일일 DB 백업(7일 보관)"
cat > /etc/cron.daily/sqlp-pg-backup <<EOF
#!/bin/sh
set -e
f=$APP_DIR/backups/sqlp-\$(date +%F).sql.gz
docker exec sqlp-platform-postgres-1 pg_dump -U sqlp sqlp | gzip > "\$f"
chmod 600 "\$f"
find $APP_DIR/backups -name 'sqlp-*.sql.gz' -mtime +7 -delete
EOF
chmod 755 /etc/cron.daily/sqlp-pg-backup

echo
echo "완료. 다음: sudo tailscale up --advertise-tags=tag:sqlp-server  (docs/deploy.md 4단계)"
