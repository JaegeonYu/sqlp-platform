package dev.sqlp.course;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 책 한 권 = 코스 한 개. 내용(장·항목)은 CourseVersion 아래에 있다.
 */
@Entity
@Table(name = "course")
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID bookId;

	@Column(nullable = false)
	private UUID ownerGroupId;

	@Column(nullable = false)
	private String title;

	private String summary;

	@Column(nullable = false)
	private UUID createdBy;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected Course() {
	}

	Course(UUID bookId, UUID ownerGroupId, String title, String summary, UUID createdBy) {
		this.bookId = bookId;
		this.ownerGroupId = ownerGroupId;
		this.title = title;
		this.summary = summary;
		this.createdBy = createdBy;
	}

	void update(String title, String summary) {
		this.title = title;
		this.summary = summary;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getBookId() {
		return this.bookId;
	}

	public UUID getOwnerGroupId() {
		return this.ownerGroupId;
	}

	public String getTitle() {
		return this.title;
	}

	public String getSummary() {
		return this.summary;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
