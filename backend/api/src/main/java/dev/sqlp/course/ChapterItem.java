package dev.sqlp.course;

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
 * 장 안의 학습 항목. PROBLEM(튜닝 문제)은 저지가 생기는 M5부터 만들 수 있다.
 */
@Entity
@Table(name = "chapter_item")
public class ChapterItem {

	public enum Type {

		THEORY_GUIDE, LAB, ASSIGNMENT, PROBLEM

	}

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID chapterId;

	@Column(nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "text")
	private String bodyMd;

	@Version
	private long version;

	protected ChapterItem() {
	}

	ChapterItem(UUID chapterId, int position, Type type, String title, String bodyMd) {
		this.chapterId = chapterId;
		this.position = position;
		this.type = type;
		this.title = title;
		this.bodyMd = bodyMd;
	}

	void update(Type type, String title, String bodyMd) {
		this.type = type;
		this.title = title;
		this.bodyMd = bodyMd;
	}

	void moveTo(int position) {
		this.position = position;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getChapterId() {
		return this.chapterId;
	}

	public int getPosition() {
		return this.position;
	}

	public Type getType() {
		return this.type;
	}

	public String getTitle() {
		return this.title;
	}

	public String getBodyMd() {
		return this.bodyMd;
	}

	public long getVersion() {
		return this.version;
	}

}
