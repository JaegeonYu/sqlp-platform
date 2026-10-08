package dev.sqlp.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class AppUser {

	static final int MAX_FAILED_LOGINS = 5;

	static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String email;

	@Column(nullable = false)
	private String nickname;

	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SystemRole systemRole = SystemRole.USER;

	private String signupNote;

	@Column(nullable = false)
	private int failedLoginCount;

	private Instant lockedUntil;

	private UUID reviewedBy;

	private Instant reviewedAt;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Version
	private long version;

	protected AppUser() {
	}

	AppUser(String email, String nickname, String passwordHash, String signupNote) {
		this.email = email;
		this.nickname = nickname;
		this.passwordHash = passwordHash;
		this.signupNote = signupNote;
		this.status = UserStatus.PENDING;
	}

	void makeBootstrapAdmin() {
		this.status = UserStatus.ACTIVE;
		this.systemRole = SystemRole.ADMIN;
	}

	boolean isLocked(Instant now) {
		return this.lockedUntil != null && this.lockedUntil.isAfter(now);
	}

	/** 실패 횟수가 한도에 이르면 잠근다. 잠갔으면 true. */
	boolean recordLoginFailure(Instant now) {
		this.failedLoginCount++;
		if (this.failedLoginCount >= MAX_FAILED_LOGINS) {
			this.failedLoginCount = 0;
			this.lockedUntil = now.plus(LOCK_DURATION);
			return true;
		}
		return false;
	}

	void recordLoginSuccess() {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
	}

	public void review(UserStatus newStatus, UUID reviewer) {
		this.status = newStatus;
		this.reviewedBy = reviewer;
		this.reviewedAt = Instant.now();
	}

	public SessionUser toSessionUser() {
		return new SessionUser(this.id, this.email, this.nickname, this.systemRole);
	}

	public UUID getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getNickname() {
		return this.nickname;
	}

	String getPasswordHash() {
		return this.passwordHash;
	}

	public UserStatus getStatus() {
		return this.status;
	}

	public SystemRole getSystemRole() {
		return this.systemRole;
	}

	public String getSignupNote() {
		return this.signupNote;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getReviewedAt() {
		return this.reviewedAt;
	}

	public boolean hasPassword() {
		return this.passwordHash != null;
	}

}
