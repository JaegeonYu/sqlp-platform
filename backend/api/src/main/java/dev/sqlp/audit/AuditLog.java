package dev.sqlp.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Instant occurredAt = Instant.now();

	private UUID actorId;

	@Column(nullable = false)
	private String action;

	private String target;

	private String ip;

	private String detail;

	protected AuditLog() {
	}

	AuditLog(UUID actorId, String action, String target, String ip, String detail) {
		this.actorId = actorId;
		this.action = action;
		this.target = target;
		this.ip = ip;
		this.detail = detail;
	}

	public UUID getActorId() {
		return this.actorId;
	}

	public String getAction() {
		return this.action;
	}

	public String getTarget() {
		return this.target;
	}

}
