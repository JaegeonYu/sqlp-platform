package dev.sqlp.course;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "chapter")
public class Chapter {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID courseVersionId;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private String title;

	private String goals;

	@Column(columnDefinition = "text")
	private String guideMd;

	/** 여러 운영진이 동시에 편집할 때 나중 저장이 앞 저장을 덮어쓰지 않도록 비교한다 */
	@Version
	private long version;

	protected Chapter() {
	}

	Chapter(UUID courseVersionId, int position, String title, String goals) {
		this.courseVersionId = courseVersionId;
		this.position = position;
		this.title = title;
		this.goals = goals;
	}

	void update(String title, String goals, String guideMd) {
		this.title = title;
		this.goals = goals;
		this.guideMd = guideMd;
	}

	void moveTo(int position) {
		this.position = position;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getCourseVersionId() {
		return this.courseVersionId;
	}

	public int getPosition() {
		return this.position;
	}

	public String getTitle() {
		return this.title;
	}

	public String getGoals() {
		return this.goals;
	}

	public String getGuideMd() {
		return this.guideMd;
	}

	public long getVersion() {
		return this.version;
	}

}
