package dev.sqlp.group;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "study_group")
public class StudyGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String name;

	private String description;

	@Column(nullable = false)
	private UUID createdBy;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected StudyGroup() {
	}

	StudyGroup(String name, String description, UUID createdBy) {
		this.name = name;
		this.description = description;
		this.createdBy = createdBy;
	}

	public UUID getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
