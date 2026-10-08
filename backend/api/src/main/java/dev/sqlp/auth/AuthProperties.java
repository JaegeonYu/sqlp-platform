package dev.sqlp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bootstrapAdminEmail 첫 관리자 이메일. 관리자가 한 명도 없을 때만 효력이 있다.
 * @param bootstrapAdminPassword 설정하면 기동 시 이메일 로그인용 첫 관리자를 만든다(12자 이상).
 * 비워 두면 같은 이메일로 Google 로그인한 사용자(이메일 인증 완료)가 첫 관리자가 된다.
 * @param googleClientId 비워 두면 Google 로그인을 끈다.
 * @param googleClientSecret Google OAuth 클라이언트 secret
 */
@ConfigurationProperties("sqlp.auth")
public record AuthProperties(String bootstrapAdminEmail, String bootstrapAdminPassword, String googleClientId,
		String googleClientSecret) {

	public boolean googleEnabled() {
		return hasText(this.googleClientId) && hasText(this.googleClientSecret);
	}

	boolean isBootstrapAdminEmail(String email) {
		return hasText(this.bootstrapAdminEmail) && this.bootstrapAdminEmail.trim().equalsIgnoreCase(email);
	}

	static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
