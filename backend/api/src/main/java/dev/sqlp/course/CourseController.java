package dev.sqlp.course;

import java.util.List;
import java.util.UUID;

import dev.sqlp.auth.SessionUser;
import dev.sqlp.course.CourseService.BookInput;
import dev.sqlp.course.CourseService.ChapterView;
import dev.sqlp.course.CourseService.CourseDetail;
import dev.sqlp.course.CourseService.CourseSummary;
import dev.sqlp.course.CourseService.ItemView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CourseController {

	private final CourseService courseService;

	CourseController(CourseService courseService) {
		this.courseService = courseService;
	}

	@PostMapping("/api/groups/{groupId}/courses")
	@ResponseStatus(HttpStatus.CREATED)
	public CourseSummary create(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@Valid @RequestBody CourseInput request) {
		return this.courseService.create(user, groupId, request.book().toInput(), request.title(),
				request.summary());
	}

	@GetMapping("/api/groups/{groupId}/courses")
	public List<CourseSummary> list(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId) {
		return this.courseService.listForGroup(user, groupId);
	}

	@GetMapping("/api/courses/{courseId}")
	public CourseDetail detail(@AuthenticationPrincipal SessionUser user, @PathVariable UUID courseId) {
		return this.courseService.detail(user, courseId);
	}

	@PatchMapping("/api/courses/{courseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(@AuthenticationPrincipal SessionUser user, @PathVariable UUID courseId,
			@Valid @RequestBody CourseInput request) {
		this.courseService.updateCourse(user, courseId, request.book().toInput(), request.title(),
				request.summary());
	}

	@PostMapping("/api/courses/{courseId}/chapters")
	@ResponseStatus(HttpStatus.CREATED)
	public ChapterView addChapter(@AuthenticationPrincipal SessionUser user, @PathVariable UUID courseId,
			@Valid @RequestBody NewChapter request) {
		return this.courseService.addChapter(user, courseId, request.title(), request.goals());
	}

	@PutMapping("/api/courses/{courseId}/chapter-order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reorderChapters(@AuthenticationPrincipal SessionUser user, @PathVariable UUID courseId,
			@Valid @RequestBody Order request) {
		this.courseService.reorderChapters(user, courseId, request.ids());
	}

	@PatchMapping("/api/chapters/{chapterId}")
	public ChapterView updateChapter(@AuthenticationPrincipal SessionUser user, @PathVariable UUID chapterId,
			@Valid @RequestBody ChapterEdit request) {
		return this.courseService.updateChapter(user, chapterId, request.version(), request.title(),
				request.goals(), request.guideMd());
	}

	@DeleteMapping("/api/chapters/{chapterId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteChapter(@AuthenticationPrincipal SessionUser user, @PathVariable UUID chapterId) {
		this.courseService.deleteChapter(user, chapterId);
	}

	@PostMapping("/api/chapters/{chapterId}/items")
	@ResponseStatus(HttpStatus.CREATED)
	public ItemView addItem(@AuthenticationPrincipal SessionUser user, @PathVariable UUID chapterId,
			@Valid @RequestBody NewItem request) {
		return this.courseService.addItem(user, chapterId, request.type(), request.title(), request.bodyMd());
	}

	@PutMapping("/api/chapters/{chapterId}/item-order")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reorderItems(@AuthenticationPrincipal SessionUser user, @PathVariable UUID chapterId,
			@Valid @RequestBody Order request) {
		this.courseService.reorderItems(user, chapterId, request.ids());
	}

	@PatchMapping("/api/chapter-items/{itemId}")
	public ItemView updateItem(@AuthenticationPrincipal SessionUser user, @PathVariable UUID itemId,
			@Valid @RequestBody ItemEdit request) {
		return this.courseService.updateItem(user, itemId, request.version(), request.type(), request.title(),
				request.bodyMd());
	}

	@DeleteMapping("/api/chapter-items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteItem(@AuthenticationPrincipal SessionUser user, @PathVariable UUID itemId) {
		this.courseService.deleteItem(user, itemId);
	}

	public record BookRequest(@NotBlank @Size(max = 200) String title, @Size(max = 200) String author,
			@Size(max = 100) String publisher, @Size(max = 20) String isbn) {

		BookInput toInput() {
			return new BookInput(this.title, this.author, this.publisher, this.isbn);
		}

	}

	public record CourseInput(@NotNull @Valid BookRequest book, @NotBlank @Size(max = 100) String title,
			@Size(max = 1000) String summary) {
	}

	public record NewChapter(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String goals) {
	}

	public record ChapterEdit(@NotNull Long version, @NotBlank @Size(max = 200) String title,
			@Size(max = 2000) String goals, @Size(max = 50000) String guideMd) {
	}

	public record NewItem(@NotNull ChapterItem.Type type, @NotBlank @Size(max = 200) String title,
			@Size(max = 50000) String bodyMd) {
	}

	public record ItemEdit(@NotNull Long version, @NotNull ChapterItem.Type type,
			@NotBlank @Size(max = 200) String title, @Size(max = 50000) String bodyMd) {
	}

	public record Order(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
	}

}
