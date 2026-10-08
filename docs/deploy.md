# 배포 가이드 (OCI Always Free + Tailscale Funnel + GitHub Actions)

비용 0원 구성이다. 서버에는 인바운드 포트를 열지 않는다.
- 사용자 접속: Tailscale Funnel(`https://sqlp.<tailnet>.ts.net`)
- 배포 접속: Tailscale 사설망 SSH(배포 스크립트 실행만 가능한 키)

```
GitHub Actions ─(OIDC)─> Tailscale(tag:ci) ─SSH 22─> 서버(tag:sqlp-server) ─> deploy.sh <sha>
사용자 ─HTTPS─> Tailscale Funnel ─> 127.0.0.1:8000(web/nginx) ─> api ─> postgres
```

## 1. OCI 계정 보호 (가입 직후)
- Billing & Cost Management → Budgets → **예산 $1, 알림 임계 100%**, 받을 이메일 지정.

## 2. 서버 생성
1. 내 PC(PowerShell)에서 접속용 키를 만든다: `ssh-keygen -t ed25519 -f $HOME\.ssh\oci_sqlp`
2. Compute → Instances → Create instance
   - Image: **Canonical Ubuntu 24.04** (aarch64, Minimal 아닌 것)
   - Shape: Ampere **VM.Standard.A1.Flex**, 4 OCPU / 24GB. "Always Free-eligible" 표시를 확인한다.
     - "Out of capacity"가 나오면 다른 AD를 고르거나 2 OCPU / 12GB로 줄여서 다시 시도한다.
   - Networking: 새 VCN + public subnet, **public IPv4 할당**(아웃바운드용)
   - SSH key: `oci_sqlp.pub` 업로드
   - Boot volume: 100GB(무료 200GB 이내)
3. VCN → Security List → Ingress의 `0.0.0.0/0 TCP 22` 출처를 **내 공인 IP/32**로 바꾼다(최초 설정용, 5단계에서 삭제).

## 3. Tailscale 준비
1. tailscale.com 가입(Personal 무료). 내 PC에도 Tailscale을 설치하고 로그인한다.
2. Access controls(정책 파일)에 아래를 합친다.
```jsonc
{
  "tagOwners": {
    "tag:sqlp-server": ["autogroup:admin"],
    "tag:ci":          ["autogroup:admin"]
  },
  "grants": [
    // 내 기기 → 서버 전체(관리용)
    { "src": ["autogroup:member"], "dst": ["tag:sqlp-server"], "ip": ["*"] },
    // CI → 서버 SSH만
    { "src": ["tag:ci"], "dst": ["tag:sqlp-server"], "ip": ["tcp:22"] }
  ],
  "nodeAttrs": [
    { "target": ["tag:sqlp-server"], "attr": ["funnel"] }
  ]
}
```
3. DNS 탭에서 **MagicDNS**와 **HTTPS Certificates**를 켠다.

## 4. 서버 초기 설정
1. 내 PC에서 배포 전용 키를 만든다: `ssh-keygen -t ed25519 -f $HOME\.ssh\sqlp_deploy -N '""' -C github-deploy`
2. 서버에 접속한다: `ssh -i $HOME\.ssh\oci_sqlp ubuntu@<공인IP>`
3. 서버에서 실행한다:
```bash
git clone https://github.com/JaegeonYu/sqlp-platform.git
sudo bash sqlp-platform/infra/server/bootstrap.sh "<sqlp_deploy.pub 내용 한 줄>"
sudo tailscale up --advertise-tags=tag:sqlp-server --hostname=sqlp --ssh=false
sudo sed -i 's/^IMAGE_OWNER=.*/IMAGE_OWNER=jaegeonyu/' /opt/sqlp/.env
sudo tailscale funnel --bg 8000
```
4. 마지막 명령이 출력하는 `https://sqlp.<tailnet>.ts.net` 주소가 서비스 주소다.

## 5. 공인 SSH 닫기
1. 내 PC에서 Tailscale 경유 접속을 확인한다: `ssh -i $HOME\.ssh\oci_sqlp ubuntu@sqlp`
2. 접속되면 서버에서 `sudo ufw delete allow 22/tcp`를 실행한다.
3. OCI Security List의 Ingress 22 규칙을 **삭제**한다.
4. 이제 서버의 인바운드 포트는 0개다.

## 6. GitHub ↔ Tailscale OIDC (저장된 Tailscale 비밀값 없음)
1. Tailscale → Settings → Trust credentials → OpenID Connect를 연다.
2. 아래 값으로 만든다.
   - Issuer: GitHub
   - Subject: `repo:JaegeonYu/sqlp-platform:environment:production`
   - Scope: `auth_keys`(write), Tags: `tag:ci`
3. 발급된 **Client ID**와 **Audience**를 7단계에서 쓴다.

## 7. GitHub 저장소 설정
1. Settings → Environments → `production`을 만든다.
   - Required reviewers: 본인
   - Deployment branches: `main`만
2. Environment secret
   - `DEPLOY_SSH_KEY`: `sqlp_deploy` **개인키** 전체
3. Environment variables
   - `TS_OIDC_CLIENT_ID`, `TS_OIDC_AUDIENCE`: 6단계 값
   - `DEPLOY_HOST`: `sqlp`
   - `DEPLOY_KNOWN_HOSTS`: 내 PC에서 `ssh-keyscan -t ed25519 sqlp`를 실행한 출력 한 줄
4. 첫 배포 후 Packages에서 `sqlp-platform-api`, `sqlp-platform-web`을 **Public**으로 바꾼다. 비공개면 무료 한도에 걸리고 서버에서 pull할 수 없다.

## 운영 메모
- 배포 흐름: main 머지 → 이미지 빌드·스캔·푸시 → `production` 승인 → 배포 → 헬스체크. 실패하면 직전 버전으로 자동 롤백한다.
- 로그: 서버에서 `cd /opt/sqlp && sudo docker compose -f compose.prod.yml --env-file .env logs -f api`
- 백업: `/opt/sqlp/backups/` 에 매일 pg_dump를 남기고 7일간 보관한다. 공개 저장소에 올리지 않는다.
- 비밀값: `/opt/sqlp/.env`(서버에만 존재)에 둔다. GitHub에는 배포 키와 Tailscale 연결 정보만 둔다.
