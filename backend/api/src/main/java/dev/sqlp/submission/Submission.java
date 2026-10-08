package dev.sqlp.submission;

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

/**
 * 과제 제출. 상태 흐름: DRAFT → SUBMITTED → (CHANGES_REQUESTED → SUBMITTED)* → APPROVED.
 * APPROVED가 되면 더 이상 수정할 수 없다.
 */
@Entity
@Table(name = "submission")
public class Submission {

	public enum Status {

		DRAFT, SUBMITTED, CHANGES_REQUESTED, APPROVED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID cohortId;

	@Column(nullable = false)
	private UUID itemId;

	@Column(nullable = false)
	private UUID authorId;

	@Column(nullable = false, columnDefinition = "text")
	private String bodyMd = "";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.DRAFT;

	private Instant submittedAt;

	private UUID reviewedBy;

	private Instant reviewedAt;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	@Column(nullable = false)
	private Instant updatedAt = Instant.now();

	@Version
	private long version;

	protected Submission() {
	}

	Submission(UUID cohortId, UUID itemId, UUID authorId) {
		this.cohortId = cohortId;
		this.itemId = itemId;
		this.authorId = authorId;
	}

	void edit(String bodyMd) {
		this.bodyMd = bodyMd;
		this.updatedAt = Instant.now();
	}

	void submit() {
		this.status = Status.SUBMITTED;
		this.submittedAt = Instant.now();
	}

	void review(Status decision, UUID reviewer) {
		this.status = decision;
		this.reviewedBy = reviewer;
		this.reviewedAt = Instant.now();
	}

	/** 다른 사람에게 보이는 상태인지(임시 저장은 본인만 본다) */
	boolean isShared() {
		return this.status != Status.DRAFT;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getCohortId() {
		return this.cohortId;
	}

	public UUID getItemId() {
		return this.itemId;
	}

	public UUID getAuthorId() {
		return this.authorId;
	}

	public String getBodyMd() {
		return this.bodyMd;
	}

	public Status getStatus() {
		return this.status;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public UUID getReviewedBy() {
		return this.reviewedBy;
	}

	public Instant getReviewedAt() {
		return this.reviewedAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public long getVersion() {
		return this.version;
	}

}
