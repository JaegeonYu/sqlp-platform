package dev.sqlp.course;

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

/**
 * 코스 내용의 스냅샷 단위. DRAFT만 편집할 수 있고, PUBLISHED는 불변이다(공개는 M7).
 */
@Entity
@Table(name = "course_version")
public class CourseVersion {

	public enum Status {

		DRAFT, PUBLISHED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID courseId;

	@Column(nullable = false)
	private int versionNo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.DRAFT;

	private Instant publishedAt;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected CourseVersion() {
	}

	CourseVersion(UUID courseId, int versionNo) {
		this.courseId = courseId;
		this.versionNo = versionNo;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getCourseId() {
		return this.courseId;
	}

	public int getVersionNo() {
		return this.versionNo;
	}

	public Status getStatus() {
		return this.status;
	}

}
