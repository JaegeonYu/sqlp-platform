package dev.sqlp.course;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.sqlp.audit.AuditService;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.common.ApiException;
import dev.sqlp.group.GroupAccess;
import dev.sqlp.group.GroupRole;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 권한 규칙
 * <ul>
 * <li>조회: 코스를 만든 그룹(owner group)의 멤버. 공개 코스 조회는 M7에서 추가한다</li>
 * <li>편집: owner group의 MANAGER 이상, 그리고 DRAFT 버전만</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CourseService {

	private final BookRepository books;

	private final CourseRepository courses;

	private final CourseVersionRepository versions;

	private final ChapterRepository chapters;

	private final ChapterItemRepository items;

	private final GroupAccess groupAccess;

	private final AuditService audit;

	CourseService(BookRepository books, CourseRepository courses, CourseVersionRepository versions,
			ChapterRepository chapters, ChapterItemRepository items, GroupAccess groupAccess, AuditService audit) {
		this.books = books;
		this.courses = courses;
		this.versions = versions;
		this.chapters = chapters;
		this.items = items;
		this.groupAccess = groupAccess;
		this.audit = audit;
	}

	@Transactional
	public CourseSummary create(SessionUser actor, UUID groupId, BookInput book, String title, String summary) {
		this.groupAccess.require(actor, groupId, GroupRole.MANAGER);
		Book savedBook = this.books.save(new Book(book.title().strip(), blankToNull(book.author()),
				blankToNull(book.publisher()), blankToNull(book.isbn())));
		Course course = this.courses
			.save(new Course(savedBook.getId(), groupId, title.strip(), blankToNull(summary), actor.id()));
		this.versions.save(new CourseVersion(course.getId(), 1));
		this.audit.record(actor.id(), "COURSE_CREATED", course.getId(), course.getTitle());
		return new CourseSummary(course.getId(), course.getTitle(), savedBook.getTitle(), 0, course.getCreatedAt());
	}

	public List<CourseSummary> listForGroup(SessionUser actor, UUID groupId) {
		this.groupAccess.require(actor, groupId, GroupRole.MEMBER);
		List<Course> found = this.courses.findByOwnerGroupIdOrderByCreatedAtDesc(groupId);
		Map<UUID, Book> bookById = this.books.findAllById(found.stream().map(Course::getBookId).toList())
			.stream()
			.collect(Collectors.toMap(Book::getId, Function.identity()));
		return found.stream().map((course) -> {
			CourseVersion latest = latestVersion(course.getId());
			return new CourseSummary(course.getId(), course.getTitle(), bookById.get(course.getBookId()).getTitle(),
					this.chapters.countByCourseVersionId(latest.getId()), course.getCreatedAt());
		}).toList();
	}

	public CourseDetail detail(SessionUser actor, UUID courseId) {
		Course course = this.courses.findById(courseId).orElseThrow(ApiException::notFound);
		GroupRole role = this.groupAccess.require(actor, course.getOwnerGroupId(), GroupRole.MEMBER);
		CourseVersion version = latestVersion(courseId);
		Book book = this.books.findById(course.getBookId()).orElseThrow();
		List<Chapter> chapterList = this.chapters.findByCourseVersionIdOrderByPosition(version.getId());
		Map<UUID, List<ItemView>> itemsByChapter = this.items
			.findByChapterIdInOrderByPosition(chapterList.stream().map(Chapter::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(ChapterItem::getChapterId,
					Collectors.mapping(ItemView::of, Collectors.toList())));
		boolean canEdit = role.atLeast(GroupRole.MANAGER) && version.getStatus() == CourseVersion.Status.DRAFT;
		return new CourseDetail(course.getId(), course.getTitle(), course.getSummary(), course.getOwnerGroupId(),
				BookInput.of(book), version.getId(), version.getVersionNo(), version.getStatus(), canEdit,
				chapterList.stream()
					.map((c) -> ChapterView.of(c, itemsByChapter.getOrDefault(c.getId(), List.of())))
					.toList());
	}

	@Transactional
	public void updateCourse(SessionUser actor, UUID courseId, BookInput book, String title, String summary) {
		Course course = editableCourse(actor, courseId);
		course.update(title.strip(), blankToNull(summary));
		this.books.findById(course.getBookId())
			.orElseThrow()
			.update(book.title().strip(), blankToNull(book.author()), blankToNull(book.publisher()),
					blankToNull(book.isbn()));
	}

	@Transactional
	public ChapterView addChapter(SessionUser actor, UUID courseId, String title, String goals) {
		editableCourse(actor, courseId);
		CourseVersion version = latestVersion(courseId);
		int position = (int) this.chapters.countByCourseVersionId(version.getId()) + 1;
		Chapter chapter = this.chapters
			.saveAndFlush(new Chapter(version.getId(), position, title.strip(), blankToNull(goals)));
		return ChapterView.of(chapter, List.of());
	}

	@Transactional
	public ChapterView updateChapter(SessionUser actor, UUID chapterId, long expectedVersion, String title,
			String goals, String guideMd) {
		Chapter chapter = editableChapter(actor, chapterId);
		if (chapter.getVersion() != expectedVersion) {
			throw ApiException.editConflict();
		}
		chapter.update(title.strip(), blankToNull(goals), blankToNull(guideMd));
		this.chapters.flush();
		return ChapterView.of(chapter, this.items.findByChapterIdOrderByPosition(chapterId)
			.stream()
			.map(ItemView::of)
			.toList());
	}

	@Transactional
	public void deleteChapter(SessionUser actor, UUID chapterId) {
		Chapter chapter = editableChapter(actor, chapterId);
		this.chapters.delete(chapter);
		this.chapters.flush();
		renumber(this.chapters.findByCourseVersionIdOrderByPosition(chapter.getCourseVersionId()),
				Chapter::moveTo);
	}

	@Transactional
	public void reorderChapters(SessionUser actor, UUID courseId, List<UUID> orderedIds) {
		editableCourse(actor, courseId);
		List<Chapter> current = this.chapters.findByCourseVersionIdOrderByPosition(latestVersion(courseId).getId());
		Map<UUID, Chapter> byId = sameSet(current, Chapter::getId, orderedIds);
		renumber(orderedIds.stream().map(byId::get).toList(), Chapter::moveTo);
	}

	@Transactional
	public ItemView addItem(SessionUser actor, UUID chapterId, ChapterItem.Type type, String title, String bodyMd) {
		editableChapter(actor, chapterId);
		rejectUnsupported(type);
		int position = this.items.findByChapterIdOrderByPosition(chapterId).size() + 1;
		ChapterItem item = this.items
			.saveAndFlush(new ChapterItem(chapterId, position, type, title.strip(), blankToNull(bodyMd)));
		return ItemView.of(item);
	}

	@Transactional
	public ItemView updateItem(SessionUser actor, UUID itemId, long expectedVersion, ChapterItem.Type type,
			String title, String bodyMd) {
		ChapterItem item = this.items.findById(itemId).orElseThrow(ApiException::notFound);
		editableChapter(actor, item.getChapterId());
		rejectUnsupported(type);
		if (item.getVersion() != expectedVersion) {
			throw ApiException.editConflict();
		}
		item.update(type, title.strip(), blankToNull(bodyMd));
		this.items.flush();
		return ItemView.of(item);
	}

	@Transactional
	public void deleteItem(SessionUser actor, UUID itemId) {
		ChapterItem item = this.items.findById(itemId).orElseThrow(ApiException::notFound);
		editableChapter(actor, item.getChapterId());
		this.items.delete(item);
		this.items.flush();
		renumber(this.items.findByChapterIdOrderByPosition(item.getChapterId()), ChapterItem::moveTo);
	}

	@Transactional
	public void reorderItems(SessionUser actor, UUID chapterId, List<UUID> orderedIds) {
		editableChapter(actor, chapterId);
		Map<UUID, ChapterItem> byId = sameSet(this.items.findByChapterIdOrderByPosition(chapterId),
				ChapterItem::getId, orderedIds);
		renumber(orderedIds.stream().map(byId::get).toList(), ChapterItem::moveTo);
	}

	/** 기수(cohort)가 코스를 고를 때 쓰는 확인: 코스가 이 그룹 소유인지 */
	public CourseVersion versionForCohort(UUID groupId, UUID courseId) {
		Course course = this.courses.findById(courseId)
			.filter((c) -> c.getOwnerGroupId().equals(groupId))
			.orElseThrow(ApiException::notFound);
		return latestVersion(course.getId());
	}

	/** 권한 확인 없이 버전의 코스·책 이름을 돌려준다. 호출하는 쪽이 접근 권한을 먼저 확인한다. */
	public CourseRef courseRef(UUID versionId) {
		CourseVersion version = this.versions.findById(versionId).orElseThrow();
		Course course = this.courses.findById(version.getCourseId()).orElseThrow();
		Book book = this.books.findById(course.getBookId()).orElseThrow();
		return new CourseRef(course.getId(), course.getTitle(), book.getTitle(), version.getVersionNo());
	}

	public List<Chapter> chaptersOf(UUID versionId) {
		return this.chapters.findByCourseVersionIdOrderByPosition(versionId);
	}

	private Course editableCourse(SessionUser actor, UUID courseId) {
		Course course = this.courses.findById(courseId).orElseThrow(ApiException::notFound);
		this.groupAccess.require(actor, course.getOwnerGroupId(), GroupRole.MANAGER);
		if (latestVersion(courseId).getStatus() != CourseVersion.Status.DRAFT) {
			throw ApiException.badRequest("NOT_DRAFT", "공개된 버전은 수정할 수 없습니다.");
		}
		return course;
	}

	private Chapter editableChapter(SessionUser actor, UUID chapterId) {
		Chapter chapter = this.chapters.findById(chapterId).orElseThrow(ApiException::notFound);
		CourseVersion version = this.versions.findById(chapter.getCourseVersionId()).orElseThrow();
		editableCourse(actor, version.getCourseId());
		return chapter;
	}

	private CourseVersion latestVersion(UUID courseId) {
		return this.versions.findFirstByCourseIdOrderByVersionNoDesc(courseId).orElseThrow();
	}

	private static void rejectUnsupported(ChapterItem.Type type) {
		if (type == ChapterItem.Type.PROBLEM) {
			throw ApiException.badRequest("UNSUPPORTED_TYPE", "튜닝 문제 항목은 채점 기능이 생긴 뒤 추가할 수 있습니다.");
		}
	}

	private static <T> Map<UUID, T> sameSet(Collection<T> current, Function<T, UUID> id, List<UUID> orderedIds) {
		if (orderedIds.size() != current.size() || !new HashSet<>(orderedIds).equals(
				current.stream().map(id).collect(Collectors.toSet()))) {
			throw ApiException.badRequest("INVALID_ORDER", "순서 목록이 현재 항목과 일치하지 않습니다.");
		}
		return current.stream().collect(Collectors.toMap(id, Function.identity()));
	}

	private static <T> void renumber(List<T> ordered, PositionSetter<T> setter) {
		for (int i = 0; i < ordered.size(); i++) {
			setter.moveTo(ordered.get(i), i + 1);
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	@FunctionalInterface
	private interface PositionSetter<T> {

		void moveTo(T target, int position);

	}

	public record BookInput(String title, String author, String publisher, String isbn) {

		static BookInput of(Book book) {
			return new BookInput(book.getTitle(), book.getAuthor(), book.getPublisher(), book.getIsbn());
		}

	}

	public record CourseSummary(UUID id, String title, String bookTitle, long chapterCount, Instant createdAt) {
	}

	public record CourseRef(UUID id, String title, String bookTitle, int versionNo) {
	}

	public record CourseDetail(UUID id, String title, String summary, UUID ownerGroupId, BookInput book,
			UUID versionId, int versionNo, CourseVersion.Status status, boolean canEdit, List<ChapterView> chapters) {
	}

	public record ChapterView(UUID id, int position, String title, String goals, String guideMd, long version,
			List<ItemView> items) {

		static ChapterView of(Chapter chapter, List<ItemView> items) {
			return new ChapterView(chapter.getId(), chapter.getPosition(), chapter.getTitle(), chapter.getGoals(),
					chapter.getGuideMd(), chapter.getVersion(), items);
		}

	}

	public record ItemView(UUID id, int position, ChapterItem.Type type, String title, String bodyMd, long version) {

		static ItemView of(ChapterItem item) {
			return new ItemView(item.getId(), item.getPosition(), item.getType(), item.getTitle(), item.getBodyMd(),
					item.getVersion());
		}

	}

}
