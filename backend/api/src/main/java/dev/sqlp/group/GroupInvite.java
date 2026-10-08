package dev.sqlp.group;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 그룹 초대 링크. 토큰 원문은 만들 때 한 번만 보여 주고, DB에는 SHA-256 해시만 저장한다.
 */
@Entity
@Table(name = "group_invite")
public class GroupInvite {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID groupId;

	@Column(nullable = false)
	private String tokenHash;

	@Column(nullable = false)
	private UUID createdBy;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int maxUses;

	@Column(nullable = false)
	private int useCount;

	@Column(nullable = false)
	private boolean revoked;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected GroupInvite() {
	}

	GroupInvite(UUID groupId, String tokenHash, UUID createdBy, Instant expiresAt, int maxUses) {
		this.groupId = groupId;
		this.tokenHash = tokenHash;
		this.createdBy = createdBy;
		this.expiresAt = expiresAt;
		this.maxUses = maxUses;
	}

	boolean isUsable(Instant now) {
		return !this.revoked && this.useCount < this.maxUses && this.expiresAt.isAfter(now);
	}

	void markUsed() {
		this.useCount++;
	}

	void revoke() {
		this.revoked = true;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getGroupId() {
		return this.groupId;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public int getMaxUses() {
		return this.maxUses;
	}

	public int getUseCount() {
		return this.useCount;
	}

	public boolean isRevoked() {
		return this.revoked;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
