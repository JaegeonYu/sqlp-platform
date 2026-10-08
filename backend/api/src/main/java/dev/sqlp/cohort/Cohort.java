package dev.sqlp.cohort;

import java.time.Instant;
import java.time.LocalDate;
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
 * 기수: 그룹이 코스 버전 하나를 골라 진행하는 한 번의 스터디.
 */
@Entity
@Table(name = "cohort")
public class Cohort {

	public enum Status {

		PLANNED, RUNNING, COMPLETED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID groupId;

	@Column(nullable = false)
	private UUID courseVersionId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalDate startsOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.PLANNED;

	@Column(nullable = false)
	private UUID createdBy;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected Cohort() {
	}

	Cohort(UUID groupId, UUID courseVersionId, String name, LocalDate startsOn, UUID createdBy) {
		this.groupId = groupId;
		this.courseVersionId = courseVersionId;
		this.name = name;
		this.startsOn = startsOn;
		this.createdBy = createdBy;
	}

	void update(String name, Status status) {
		this.name = name;
		this.status = status;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getGroupId() {
		return this.groupId;
	}

	public UUID getCourseVersionId() {
		return this.courseVersionId;
	}

	public String getName() {
		return this.name;
	}

	public LocalDate getStartsOn() {
		return this.startsOn;
	}

	public Status getStatus() {
		return this.status;
	}

}
