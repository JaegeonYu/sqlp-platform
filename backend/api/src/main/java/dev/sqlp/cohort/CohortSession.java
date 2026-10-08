package dev.sqlp.cohort;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기수의 장별 일정(모임 일시, 발표자).
 */
@Entity
@Table(name = "cohort_session")
public class CohortSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID cohortId;

	@Column(nullable = false)
	private UUID chapterId;

	private Instant scheduledAt;

	private UUID presenterId;

	private String note;

	protected CohortSession() {
	}

	CohortSession(UUID cohortId, UUID chapterId, Instant scheduledAt) {
		this.cohortId = cohortId;
		this.chapterId = chapterId;
		this.scheduledAt = scheduledAt;
	}

	void update(Instant scheduledAt, UUID presenterId, String note) {
		this.scheduledAt = scheduledAt;
		this.presenterId = presenterId;
		this.note = note;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getChapterId() {
		return this.chapterId;
	}

	public Instant getScheduledAt() {
		return this.scheduledAt;
	}

	public UUID getPresenterId() {
		return this.presenterId;
	}

	public String getNote() {
		return this.note;
	}

}
