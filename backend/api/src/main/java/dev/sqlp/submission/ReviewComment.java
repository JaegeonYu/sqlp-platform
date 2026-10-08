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

@Entity
@Table(name = "review_comment")
public class ReviewComment {

	public enum Decision {

		APPROVE, REQUEST_CHANGES

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID submissionId;

	@Column(nullable = false)
	private UUID authorId;

	private UUID parentId;

	@Enumerated(EnumType.STRING)
	private Decision decision;

	@Column(nullable = false, columnDefinition = "text")
	private String bodyMd;

	@Column(nullable = false)
	private boolean deleted;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant editedAt;

	protected ReviewComment() {
	}

	ReviewComment(UUID submissionId, UUID authorId, UUID parentId, Decision decision, String bodyMd) {
		this.submissionId = submissionId;
		this.authorId = authorId;
		this.parentId = parentId;
		this.decision = decision;
		this.bodyMd = bodyMd;
	}

	void edit(String bodyMd) {
		this.bodyMd = bodyMd;
		this.editedAt = Instant.now();
	}

	/** 답글 구조를 유지하려고 행은 남기고 내용만 지운다 */
	void delete() {
		this.deleted = true;
		this.bodyMd = "";
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getSubmissionId() {
		return this.submissionId;
	}

	public UUID getAuthorId() {
		return this.authorId;
	}

	public UUID getParentId() {
		return this.parentId;
	}

	public Decision getDecision() {
		return this.decision;
	}

	public String getBodyMd() {
		return this.bodyMd;
	}

	public boolean isDeleted() {
		return this.deleted;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getEditedAt() {
		return this.editedAt;
	}

}
