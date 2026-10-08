# sqlp-platform

책 한 권 단위로 스터디를 운영하고, 완주한 과정을 코스로 공개해 다른 그룹이 재사용하는 스터디 플랫폼이다.
Oracle SQL 튜닝 문제를 제출하면 결과와 Buffers로 채점하는 저지(M5~)를 붙인다.

- 백엔드: Spring Boot 4.1 (Java 21), PostgreSQL, Spring Session JDBC
- 프론트: React + Vite + TanStack Query
- 채점 DB: Oracle 23ai Free (M5부터)
- 배포: [docs/deploy.md](docs/deploy.md)

## 로컬 실행
Docker만 있으면 된다(Windows는 WSL의 Docker Engine 기준).

```bash
cp infra/.env.example infra/.env
# infra/.env 에서 BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD(12자 이상)를 채운다
docker compose -f infra/compose.yml --env-file infra/.env up -d --build
```
http://localhost:8000 에서 확인한다.
1. `.env`에 넣은 관리자 계정으로 로그인한다.
2. 다른 브라우저(또는 시크릿 창)에서 가입을 신청한다.
3. 관리자 화면 "회원 관리"에서 승인한다.

## 인증과 가입
- 가입 경로는 이메일 가입과 Google 로그인 두 가지다. 어느 쪽이든 `PENDING` 상태로 시작한다. **시스템 관리자가 승인해야 로그인할 수 있다.**
- 첫 관리자
  - 이메일 관리자: `BOOTSTRAP_ADMIN_EMAIL`과 `BOOTSTRAP_ADMIN_PASSWORD`를 설정하면 기동할 때 만든다.
  - Google 관리자: 이메일만 설정하면 그 이메일로 처음 Google 로그인한 사용자(이메일 인증 완료)가 관리자가 된다.
  - 둘 다 관리자가 한 명도 없을 때만 동작한다. 공개 가입 경로로는 관리자가 될 수 없다.
- Google 로그인(선택)
  1. Google Cloud Console → API 및 서비스 → OAuth 동의 화면을 만든다. 범위는 `openid`, `email`, `profile`만 쓴다.
  2. 사용자 인증 정보에서 OAuth 클라이언트 ID를 만든다(웹 애플리케이션).
     - 승인된 리디렉션 URI: `http://localhost:8000/login/oauth2/code/google`, 운영 주소 `https://<서비스 주소>/login/oauth2/code/google`
  3. `GOOGLE_CLIENT_ID`와 `GOOGLE_CLIENT_SECRET`을 `.env`에 넣는다. 비워 두면 Google 버튼이 나타나지 않는다.
- 이미 이메일로 가입한 주소로 Google 로그인하면 거절한다. 이메일이 같다고 계정을 자동으로 연결하지 않는다(계정 탈취 방지).

## 보안 기본값 (M1)
| 항목 | 내용 |
|---|---|
| 비밀번호 | Argon2 해시. 10자 이상 |
| 로그인 실패 | 5회 실패하면 15분 잠금. 계정이 없을 때와 비밀번호가 틀렸을 때 같은 메시지·같은 처리 시간 |
| 세션 | `SQLP_SESSION` 쿠키(HttpOnly, Secure, SameSite=Lax), PostgreSQL 저장, 8시간. 로그인할 때 세션 ID를 새로 만든다 |
| 상태 반영 | 요청마다 계정 상태를 다시 확인해서, 정지된 사용자의 기존 세션을 즉시 끊는다 |
| CSRF | Spring Security SPA 방식(`XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더) |
| 요청 제한 | 로그인·가입: IP당 분당 20회, 이메일당 15분 10회. 초대 링크: 사용자당 10분 30회. 초과하면 `429` + `Retry-After` |
| 그룹 권한 | 멤버가 아니면 404(존재 여부를 숨김). 역할별 권한은 `GroupService` 주석 참고 |
| 초대 링크 | 256비트 랜덤 토큰. DB에는 SHA-256 해시만 저장. 링크는 `#` 뒤에 붙여 서버 로그에 남지 않게 함 |
| 감사 로그 | 가입, 로그인 성공·실패·잠금, 승인·정지, 그룹 역할 변경, 초대 생성·수락 (`audit_log`) |

## 테스트
```bash
cd backend && ./gradlew build
```
Testcontainers로 PostgreSQL을 띄워 통합 테스트를 실행한다. 가입 승인, 잠금, CSRF, 세션 쿠키 속성, 권한, 초대, 요청 제한을 검사한다.
