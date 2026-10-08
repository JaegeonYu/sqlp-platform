package dev.sqlp.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 외부 로그인 연결. provider + subject(Google의 sub)로 사용자를 찾는다. 이메일로는 연결하지 않는다.
 */
@Entity
@Table(name = "user_identity")
public class UserIdentity {

	public static final String GOOGLE = "GOOGLE";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String provider;

	@Column(nullable = false)
	private String subject;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected UserIdentity() {
	}

	UserIdentity(UUID userId, String provider, String subject) {
		this.userId = userId;
		this.provider = provider;
		this.subject = subject;
	}

	UUID getUserId() {
		return this.userId;
	}

}
