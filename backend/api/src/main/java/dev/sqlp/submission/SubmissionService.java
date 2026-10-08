package dev.sqlp.submission;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.sqlp.audit.AuditService;
import dev.sqlp.auth.AppUser;
import dev.sqlp.auth.AppUserRepository;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.cohort.Cohort;
import dev.sqlp.cohort.CohortService;
import dev.sqlp.common.ApiException;
import dev.sqlp.course.CourseService;
import dev.sqlp.course.CourseService.AssignmentRef;
import dev.sqlp.group.GroupAccess;
import dev.sqlp.group.GroupRole;
import dev.sqlp.group.GroupService;
import dev.sqlp.group.GroupService.MemberView;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과제 제출·리뷰 규칙
 * <ul>
 * <li>기수 그룹의 멤버만 접근한다(아니면 404)</li>
 * <li>임시 저장(DRAFT)은 본인만 본다</li>
 * <li>다른 사람의 제출물은 ① 내가 먼저 제출했거나 ② 마감이 지났거나 ③ 내가 MANAGER 이상일 때 본다(정답 베끼기 방지)</li>
 * <li>리뷰(승인·수정 요청)는 작성자가 아닌 멤버가, 제출(SUBMITTED) 상태에서만 한다</li>
 * <li>APPROVED가 되면 작성자도 수정할 수 없다</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SubmissionService {

	private final SubmissionRepository submissions;

	private final ReviewCommentRepository comments;

	private final CohortService cohortService;

	private final CourseService courseService;

	private final GroupService groupService;

	private final GroupAccess groupAccess;

	private final AppUserRepository users;

	private final AuditService audit;

	SubmissionService(SubmissionRepository submissions, ReviewCommentRepository comments,
			CohortService cohortService, CourseService courseService, GroupService groupService,
			GroupAccess groupAccess, AppUserRepository users, AuditService audit) {
		this.submissions = submissions;
		this.comments = comments;
		this.cohortService = cohortService;
		this.courseService = courseService;
		this.groupService = groupService;
		this.groupAccess = groupAccess;
		this.users = users;
		this.audit = audit;
	}

	public List<AssignmentView> assignments(SessionUser actor, UUID cohortId) {
		CohortContext ctx = cohortContext(actor, cohortId);
		Map<UUID, List<Submission>> byItem = this.submissions.findByCohortId(cohortId)
			.stream()
			.collect(Collectors.groupingBy(Submission::getItemId));
		return ctx.assignments.stream().map((a) -> {
			List<Submission> list = byItem.getOrDefault(a.itemId(), List.of());
			Optional<Submission> mine = list.stream().filter((s) -> s.getAuthorId().equals(actor.id())).findFirst();
			return new AssignmentView(a.itemId(), a.title(), a.descriptionMd(), a.chapterPosition(), a.chapterTitle(),
					ctx.dueAt(a), mine.map(Submission::getStatus).orElse(null),
					list.stream().filter(Submission::isShared).count(),
					list.stream().filter((s) -> s.getStatus() == Submission.Status.APPROVED).count());
		}).toList();
	}

	/** 멤버×과제 진척도. 상태만 보여 주고 내용은 담지 않으므로 모든 멤버가 볼 수 있다. */
	public ProgressView progress(SessionUser actor, UUID cohortId) {
		CohortContext ctx = cohortContext(actor, cohortId);
		List<MemberView> members = this.groupService.members(actor, ctx.cohort.getGroupId());
		Map<String, Submission> byKey = this.submissions.findByCohortId(cohortId)
			.stream()
			.collect(Collectors.toMap((s) -> s.getAuthorId() + "|" + s.getItemId(), Function.identity()));
		List<ProgressColumn> columns = ctx.assignments.stream()
			.map((a) -> new ProgressColumn(a.itemId(), a.title(), a.chapterPosition(), ctx.dueAt(a)))
			.toList();
		List<ProgressRow> rows = members.stream().map((m) -> new ProgressRow(m.userId(), m.nickname(),
				ctx.assignments.stream().map((a) -> {
					Submission s = byKey.get(m.userId() + "|" + a.itemId());
					if (s == null) {
						return new ProgressCell(a.itemId(), null, null, false);
					}
					return new ProgressCell(a.itemId(), s.getId(), s.getStatus(), isLate(s, ctx.dueAt(a)));
				}).toList()))
			.toList();
		return new ProgressView(columns, rows);
	}

	public SubmissionList list(SessionUser actor, UUID cohortId, UUID itemId) {
		ItemContext ctx = itemContext(actor, cohortId, itemId);
		List<Submission> all = this.submissions.findByCohortIdAndItemId(cohortId, itemId);
		Optional<Submission> mine = all.stream().filter((s) -> s.getAuthorId().equals(actor.id())).findFirst();
		boolean canViewOthers = canViewOthers(ctx, mine.orElse(null));
		List<Submission> others = canViewOthers ? all.stream()
			.filter((s) -> !s.getAuthorId().equals(actor.id()) && s.isShared())
			.toList() : List.of();
		Map<UUID, String> names = nicknames(all.stream().map(Submission::getAuthorId).toList());
		Map<UUID, Long> commentCounts = commentCounts(all.stream().map(Submission::getId).toList());
		Function<Submission, SubmissionSummary> summary = (s) -> new SubmissionSummary(s.getId(), s.getAuthorId(),
				names.get(s.getAuthorId()), s.getStatus(), s.getSubmittedAt(), isLate(s, ctx.dueAt),
				commentCounts.getOrDefault(s.getId(), 0L));
		return new SubmissionList(ctx.assignment.itemId(), ctx.assignment.title(), ctx.assignment.descriptionMd(),
				ctx.dueAt, canViewOthers, mine.map(summary).orElse(null), others.stream().map(summary).toList());
	}

	@Transactional
	public SubmissionDetail saveMine(SessionUser actor, UUID cohortId, UUID itemId, String bodyMd,
			Long expectedVersion) {
		ItemContext ctx = itemContext(actor, cohortId, itemId);
		Submission submission = this.submissions.findByCohortIdAndItemIdAndAuthorId(cohortId, itemId, actor.id())
			.orElse(null);
		if (submission == null) {
			submission = this.submissions.save(new Submission(cohortId, itemId, actor.id()));
		}
		else if (submission.getStatus() == Submission.Status.APPROVED) {
			throw ApiException.badRequest("ALREADY_APPROVED", "승인된 제출물은 수정할 수 없습니다.");
		}
		else if (expectedVersion == null || submission.getVersion() != expectedVersion) {
			throw ApiException.editConflict();
		}
		submission.edit(bodyMd);
		this.submissions.flush();
		return detail(actor, submission, ctx);
	}

	@Transactional
	public SubmissionDetail submitMine(SessionUser actor, UUID cohortId, UUID itemId) {
		ItemContext ctx = itemContext(actor, cohortId, itemId);
		Submission submission = this.submissions.findByCohortIdAndItemIdAndAuthorId(cohortId, itemId, actor.id())
			.orElseThrow(() -> ApiException.badRequest("EMPTY_SUBMISSION", "먼저 내용을 작성해 저장하세요."));
		if (submission.getBodyMd().isBlank()) {
			throw ApiException.badRequest("EMPTY_SUBMISSION", "먼저 내용을 작성해 저장하세요.");
		}
		switch (submission.getStatus()) {
			case DRAFT, CHANGES_REQUESTED -> {
				submission.submit();
				this.audit.record(actor.id(), "SUBMISSION_SUBMITTED", submission.getId(), null);
			}
			case SUBMITTED -> {
			}
			case APPROVED -> throw ApiException.badRequest("ALREADY_APPROVED", "이미 승인된 제출물입니다.");
		}
		this.submissions.flush();
		return detail(actor, submission, ctx);
	}

	public SubmissionDetail detail(SessionUser actor, UUID submissionId) {
		Submission submission = this.submissions.findById(submissionId).orElseThrow(ApiException::notFound);
		ItemContext ctx = itemContext(actor, submission.getCohortId(), submission.getItemId());
		requireVisible(actor, ctx, submission);
		return detail(actor, submission, ctx);
	}

	@Transactional
	public CommentView comment(SessionUser actor, UUID submissionId, String bodyMd, UUID parentId) {
		Submission submission = this.submissions.findById(submissionId).orElseThrow(ApiException::notFound);
		ItemContext ctx = itemContext(actor, submission.getCohortId(), submission.getItemId());
		requireVisible(actor, ctx, submission);
		if (!submission.isShared()) {
			throw ApiException.badRequest("NOT_SUBMITTED", "제출한 뒤에 코멘트를 달 수 있습니다.");
		}
		if (parentId != null) {
			ReviewComment parent = this.comments.findById(parentId)
				.filter((c) -> c.getSubmissionId().equals(submissionId) && c.getParentId() == null)
				.orElseThrow(() -> ApiException.badRequest("INVALID_PARENT", "답글을 달 수 없는 코멘트입니다."));
			parentId = parent.getId();
		}
		ReviewComment saved = this.comments
			.save(new ReviewComment(submissionId, actor.id(), parentId, null, bodyMd.strip()));
		return CommentView.of(saved, actor.nickname(), true);
	}

	@Transactional
	public CommentView review(SessionUser actor, UUID submissionId, ReviewComment.Decision decision,
			String bodyMd) {
		Submission submission = this.submissions.findById(submissionId).orElseThrow(ApiException::notFound);
		ItemContext ctx = itemContext(actor, submission.getCohortId(), submission.getItemId());
		requireVisible(actor, ctx, submission);
		if (submission.getAuthorId().equals(actor.id())) {
			throw ApiException.badRequest("SELF_REVIEW", "자기 제출물은 리뷰할 수 없습니다.");
		}
		if (submission.getStatus() != Submission.Status.SUBMITTED) {
			throw ApiException.badRequest("NOT_REVIEWABLE", "제출 상태인 과제만 리뷰할 수 있습니다.");
		}
		Submission.Status next = (decision == ReviewComment.Decision.APPROVE) ? Submission.Status.APPROVED
				: Submission.Status.CHANGES_REQUESTED;
		submission.review(next, actor.id());
		String body = (bodyMd == null || bodyMd.isBlank())
				? ((decision == ReviewComment.Decision.APPROVE) ? "승인했습니다." : "수정을 요청했습니다.") : bodyMd.strip();
		ReviewComment saved = this.comments.save(new ReviewComment(submissionId, actor.id(), null, decision, body));
		this.audit.record(actor.id(), "SUBMISSION_" + next.name(), submissionId, null);
		return CommentView.of(saved, actor.nickname(), true);
	}

	@Transactional
	public CommentView editComment(SessionUser actor, UUID commentId, String bodyMd) {
		ReviewComment comment = this.comments.findById(commentId).orElseThrow(ApiException::notFound);
		Submission submission = this.submissions.findById(comment.getSubmissionId()).orElseThrow();
		ItemContext ctx = itemContext(actor, submission.getCohortId(), submission.getItemId());
		requireVisible(actor, ctx, submission);
		if (!comment.getAuthorId().equals(actor.id()) || comment.isDeleted()) {
			throw ApiException.forbidden();
		}
		comment.edit(bodyMd.strip());
		return CommentView.of(comment, actor.nickname(), true);
	}

	@Transactional
	public void deleteComment(SessionUser actor, UUID commentId) {
		ReviewComment comment = this.comments.findById(commentId).orElseThrow(ApiException::notFound);
		Submission submission = this.submissions.findById(comment.getSubmissionId()).orElseThrow();
		ItemContext ctx = itemContext(actor, submission.getCohortId(), submission.getItemId());
		requireVisible(actor, ctx, submission);
		if (!comment.getAuthorId().equals(actor.id()) && !ctx.role.atLeast(GroupRole.MANAGER)) {
			throw ApiException.forbidden();
		}
		comment.delete();
		this.audit.record(actor.id(), "REVIEW_COMMENT_DELETED", commentId, null);
	}

	// --- 내부 ---

	private SubmissionDetail detail(SessionUser actor, Submission submission, ItemContext ctx) {
		List<ReviewComment> thread = this.comments.findBySubmissionIdOrderByCreatedAt(submission.getId());
		Map<UUID, String> names = nicknames(
				java.util.stream.Stream
					.concat(java.util.stream.Stream.of(submission.getAuthorId()),
							thread.stream().map(ReviewComment::getAuthorId))
					.toList());
		boolean isAuthor = submission.getAuthorId().equals(actor.id());
		boolean canModerate = ctx.role.atLeast(GroupRole.MANAGER);
		return new SubmissionDetail(submission.getId(), submission.getCohortId(), ctx.assignment.itemId(),
				ctx.assignment.title(), ctx.assignment.descriptionMd(), ctx.dueAt, submission.getAuthorId(),
				names.get(submission.getAuthorId()), submission.getBodyMd(), submission.getStatus(),
				submission.getSubmittedAt(), isLate(submission, ctx.dueAt), submission.getUpdatedAt(),
				submission.getVersion(),
				isAuthor && submission.getStatus() != Submission.Status.APPROVED,
				!isAuthor && submission.getStatus() == Submission.Status.SUBMITTED,
				thread.stream()
					.map((c) -> CommentView.of(c, names.get(c.getAuthorId()),
							!c.isDeleted() && (c.getAuthorId().equals(actor.id()) || canModerate)))
					.toList());
	}

	private void requireVisible(SessionUser actor, ItemContext ctx, Submission submission) {
		if (submission.getAuthorId().equals(actor.id())) {
			return;
		}
		if (!submission.isShared()) {
			throw ApiException.notFound();
		}
		Submission mine = this.submissions
			.findByCohortIdAndItemIdAndAuthorId(submission.getCohortId(), submission.getItemId(), actor.id())
			.orElse(null);
		if (!canViewOthers(ctx, mine)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "SUBMIT_FIRST",
					"내 과제를 먼저 제출하면 다른 사람의 제출물을 볼 수 있습니다.");
		}
	}

	private static boolean canViewOthers(ItemContext ctx, Submission mine) {
		return ctx.role.atLeast(GroupRole.MANAGER) || (mine != null && mine.isShared())
				|| (ctx.dueAt != null && Instant.now().isAfter(ctx.dueAt));
	}

	private static boolean isLate(Submission submission, Instant dueAt) {
		return dueAt != null && submission.getSubmittedAt() != null && submission.getSubmittedAt().isAfter(dueAt);
	}

	private CohortContext cohortContext(SessionUser actor, UUID cohortId) {
		Cohort cohort = this.cohortService.find(cohortId);
		GroupRole role = this.groupAccess.require(actor, cohort.getGroupId(), GroupRole.MEMBER);
		return new CohortContext(cohort, role, this.courseService.assignmentsOf(cohort.getCourseVersionId()),
				this.cohortService.meetingTimes(cohortId));
	}

	private ItemContext itemContext(SessionUser actor, UUID cohortId, UUID itemId) {
		CohortContext ctx = cohortContext(actor, cohortId);
		AssignmentRef assignment = ctx.assignments.stream()
			.filter((a) -> a.itemId().equals(itemId))
			.findFirst()
			.orElseThrow(ApiException::notFound);
		return new ItemContext(ctx.cohort, ctx.role, assignment, ctx.dueAt(assignment));
	}

	private Map<UUID, String> nicknames(Collection<UUID> ids) {
		Map<UUID, String> result = new HashMap<>();
		for (AppUser user : this.users.findAllById(ids.stream().distinct().toList())) {
			result.put(user.getId(), user.getNickname());
		}
		return result;
	}

	private Map<UUID, Long> commentCounts(Collection<UUID> submissionIds) {
		if (submissionIds.isEmpty()) {
			return Map.of();
		}
		return this.comments.countBySubmissionIds(submissionIds)
			.stream()
			.collect(Collectors.toMap((row) -> (UUID) row[0], (row) -> (Long) row[1]));
	}

	private record CohortContext(Cohort cohort, GroupRole role, List<AssignmentRef> assignments,
			Map<UUID, Instant> meetingTimes) {

		Instant dueAt(AssignmentRef assignment) {
			return this.meetingTimes.get(assignment.chapterId());
		}

	}

	private record ItemContext(Cohort cohort, GroupRole role, AssignmentRef assignment, Instant dueAt) {
	}

	public record AssignmentView(UUID itemId, String title, String descriptionMd, int chapterPosition,
			String chapterTitle, Instant dueAt, Submission.Status myStatus, long submittedCount,
			long approvedCount) {
	}

	public record ProgressColumn(UUID itemId, String title, int chapterPosition, Instant dueAt) {
	}

	public record ProgressCell(UUID itemId, UUID submissionId, Submission.Status status, boolean late) {
	}

	public record ProgressRow(UUID userId, String nickname, List<ProgressCell> cells) {
	}

	public record ProgressView(List<ProgressColumn> assignments, List<ProgressRow> members) {
	}

	public record SubmissionSummary(UUID id, UUID authorId, String authorNickname, Submission.Status status,
			Instant submittedAt, boolean late, long commentCount) {
	}

	public record SubmissionList(UUID itemId, String title, String descriptionMd, Instant dueAt,
			boolean canViewOthers, SubmissionSummary mine, List<SubmissionSummary> others) {
	}

	public record CommentView(UUID id, UUID authorId, String authorNickname, UUID parentId,
			ReviewComment.Decision decision, String bodyMd, boolean deleted, Instant createdAt, Instant editedAt,
			boolean canModify) {

		static CommentView of(ReviewComment c, String nickname, boolean canModify) {
			return new CommentView(c.getId(), c.getAuthorId(), nickname, c.getParentId(), c.getDecision(),
					c.getBodyMd(), c.isDeleted(), c.getCreatedAt(), c.getEditedAt(), canModify);
		}

	}

	public record SubmissionDetail(UUID id, UUID cohortId, UUID itemId, String title, String descriptionMd,
			Instant dueAt, UUID authorId, String authorNickname, String bodyMd, Submission.Status status,
			Instant submittedAt, boolean late, Instant updatedAt, long version, boolean canEdit, boolean canReview,
			List<CommentView> comments) {
	}

}
