package dev.sqlp.group;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "membership")
public class Membership {

	@EmbeddedId
	private Key id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GroupRole role;

	@Column(nullable = false)
	private Instant joinedAt = Instant.now();

	protected Membership() {
	}

	Membership(UUID groupId, UUID userId, GroupRole role) {
		this.id = new Key(groupId, userId);
		this.role = role;
	}

	public Key getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.id.userId;
	}

	public GroupRole getRole() {
		return this.role;
	}

	void changeRole(GroupRole role) {
		this.role = role;
	}

	public Instant getJoinedAt() {
		return this.joinedAt;
	}

	@Embeddable
	public record Key(@Column(name = "group_id") UUID groupId, @Column(name = "user_id") UUID userId)
			implements Serializable {
	}

}
