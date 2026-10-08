package dev.sqlp.auth;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import dev.sqlp.audit.AuditService;
import dev.sqlp.common.ApiException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final AppUserRepository users;

	private final UserIdentityRepository identities;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	private final AuthProperties properties;

	/** 존재하지 않는 계정에도 같은 시간만큼 해시 비교를 해서 응답 시간으로 계정 존재 여부를 알 수 없게 한다 */
	private final String dummyHash;

	public AuthService(AppUserRepository users, UserIdentityRepository identities, PasswordEncoder passwordEncoder,
			AuditService audit, AuthProperties properties) {
		this.users = users;
		this.identities = identities;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.properties = properties;
		this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
	}

	/**
	 * 가입 신청. 이미 있는 이메일이어도 같은 응답을 주기 위해 조용히 끝낸다.
	 */
	public void signup(String email, String nickname, String password, String note) {
		String normalized = normalizeEmail(email);
		if (this.users.findByEmail(normalized).isPresent()) {
			this.audit.record(null, "SIGNUP_DUPLICATE", normalized, null);
			return;
		}
		AppUser user = new AppUser(normalized, nickname.strip(), this.passwordEncoder.encode(password),
				blankToNull(note));
		try {
			this.users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// 같은 이메일이 동시에 가입된 경우
			this.audit.record(null, "SIGNUP_DUPLICATE", normalized, null);
			return;
		}
		this.audit.record(user.getId(), "SIGNUP", normalized, "local");
	}

	@Transactional(noRollbackFor = ApiException.class)
	public SessionUser authenticate(String email, String password) {
		String normalized = normalizeEmail(email);
		Optional<AppUser> found = this.users.findByEmail(normalized);
		if (found.isEmpty() || !found.get().hasPassword()) {
			this.passwordEncoder.matches(password, this.dummyHash);
			this.audit.record(null, "LOGIN_FAILURE", normalized, "unknown account");
			throw invalidCredentials();
		}
		AppUser user = found.get();
		Instant now = Instant.now();
		if (user.isLocked(now)) {
			this.passwordEncoder.matches(password, this.dummyHash);
			this.audit.record(user.getId(), "LOGIN_FAILURE", normalized, "locked");
			throw invalidCredentials();
		}
		if (!this.passwordEncoder.matches(password, user.getPasswordHash())) {
			boolean locked = user.recordLoginFailure(now);
			this.audit.record(user.getId(), locked ? "LOGIN_LOCKED" : "LOGIN_FAILURE", normalized, "bad password");
			throw invalidCredentials();
		}
		user.recordLoginSuccess();
		// 비밀번호가 맞은 뒤에만 상태를 알려 준다(계정 존재 여부 노출 아님)
		checkActive(user);
		this.audit.record(user.getId(), "LOGIN_SUCCESS", normalized, "local");
		return user.toSessionUser();
	}

	@Transactional
	public GoogleLoginResult loginWithGoogle(String subject, String email, boolean emailVerified, String name) {
		Optional<UserIdentity> identity = this.identities.findByProviderAndSubject(UserIdentity.GOOGLE, subject);
		AppUser user;
		if (identity.isPresent()) {
			user = this.users.findById(identity.get().getUserId()).orElseThrow();
		}
		else {
			if (!emailVerified || email == null) {
				return GoogleLoginResult.of(GoogleLoginResult.Outcome.EMAIL_NOT_VERIFIED);
			}
			String normalized = normalizeEmail(email);
			if (this.users.findByEmail(normalized).isPresent()) {
				// 이메일만 같다고 기존 계정에 연결하지 않는다(계정 탈취 방지)
				this.audit.record(null, "GOOGLE_EMAIL_IN_USE", normalized, null);
				return GoogleLoginResult.of(GoogleLoginResult.Outcome.EMAIL_IN_USE);
			}
			user = new AppUser(normalized, nicknameFrom(name, normalized), null, null);
			if (this.properties.isBootstrapAdminEmail(normalized)
					&& !this.users.existsBySystemRole(SystemRole.ADMIN)) {
				user.makeBootstrapAdmin();
			}
			this.users.save(user);
			this.identities.save(new UserIdentity(user.getId(), UserIdentity.GOOGLE, subject));
			this.audit.record(user.getId(), "SIGNUP", normalized,
					"google" + (user.getSystemRole() == SystemRole.ADMIN ? " bootstrap-admin" : ""));
		}
		return switch (user.getStatus()) {
			case ACTIVE -> {
				this.audit.record(user.getId(), "LOGIN_SUCCESS", user.getEmail(), "google");
				yield new GoogleLoginResult(GoogleLoginResult.Outcome.ACTIVE, user.toSessionUser());
			}
			case PENDING -> GoogleLoginResult.of(GoogleLoginResult.Outcome.PENDING);
			case REJECTED, SUSPENDED -> GoogleLoginResult.of(GoogleLoginResult.Outcome.INACTIVE);
		};
	}

	private static void checkActive(AppUser user) {
		switch (user.getStatus()) {
			case ACTIVE -> {
			}
			case PENDING -> throw new ApiException(HttpStatus.FORBIDDEN, "PENDING_APPROVAL",
					"관리자 승인을 기다리고 있습니다. 승인 후 로그인할 수 있습니다.");
			case REJECTED, SUSPENDED -> throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE",
					"사용할 수 없는 계정입니다. 관리자에게 문의하세요.");
		}
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
				"이메일 또는 비밀번호가 올바르지 않습니다. 여러 번 실패하면 잠시 로그인이 제한됩니다.");
	}

	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static String nicknameFrom(String name, String email) {
		String candidate = (name != null && !name.isBlank()) ? name.strip() : email.substring(0, email.indexOf('@'));
		return (candidate.length() > 40) ? candidate.substring(0, 40) : candidate;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	public record GoogleLoginResult(Outcome outcome, SessionUser user) {

		public enum Outcome {

			ACTIVE, PENDING, INACTIVE, EMAIL_IN_USE, EMAIL_NOT_VERIFIED

		}

		static GoogleLoginResult of(Outcome outcome) {
			return new GoogleLoginResult(outcome, null);
		}

	}

}
