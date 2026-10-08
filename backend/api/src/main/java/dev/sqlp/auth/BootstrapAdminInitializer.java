package dev.sqlp.auth;

import dev.sqlp.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * BOOTSTRAP_ADMIN_EMAIL + BOOTSTRAP_ADMIN_PASSWORD가 설정돼 있고 관리자가 아직 없으면 첫 관리자를 만든다.
 * 공개 가입 경로로는 관리자가 될 수 없게 하려고, 이메일 로그인 관리자는 이 방법으로만 만든다.
 */
@Component
class BootstrapAdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

	private static final int MIN_PASSWORD_LENGTH = 12;

	private final AuthProperties properties;

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AuditService audit;

	BootstrapAdminInitializer(AuthProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder,
			AuditService audit) {
		this.properties = properties;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!AuthProperties.hasText(this.properties.bootstrapAdminEmail())
				|| !AuthProperties.hasText(this.properties.bootstrapAdminPassword())
				|| this.users.existsBySystemRole(SystemRole.ADMIN)) {
			return;
		}
		if (this.properties.bootstrapAdminPassword().length() < MIN_PASSWORD_LENGTH) {
			log.warn("BOOTSTRAP_ADMIN_PASSWORD가 {}자 미만이라 첫 관리자를 만들지 않았습니다.", MIN_PASSWORD_LENGTH);
			return;
		}
		String email = AuthService.normalizeEmail(this.properties.bootstrapAdminEmail());
		if (this.users.findByEmail(email).isPresent()) {
			log.warn("첫 관리자 이메일({})로 이미 가입한 계정이 있어 자동 생성을 건너뜁니다.", email);
			return;
		}
		AppUser admin = new AppUser(email, "관리자",
				this.passwordEncoder.encode(this.properties.bootstrapAdminPassword()), null);
		admin.makeBootstrapAdmin();
		this.users.save(admin);
		this.audit.record(admin.getId(), "BOOTSTRAP_ADMIN", email, "local");
		log.info("첫 관리자 계정을 만들었습니다: {}", email);
	}

}
