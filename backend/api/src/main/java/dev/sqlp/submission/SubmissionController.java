package dev.sqlp.submission;

import java.util.List;
import java.util.UUID;

import dev.sqlp.auth.SessionUser;
import dev.sqlp.security.RateLimiter;
import dev.sqlp.security.RateLimiter.Plan;
import dev.sqlp.submission.SubmissionService.AssignmentView;
import dev.sqlp.submission.SubmissionService.CommentView;
import dev.sqlp.submission.SubmissionService.ProgressView;
import dev.sqlp.submission.SubmissionService.SubmissionDetail;
import dev.sqlp.submission.SubmissionService.SubmissionList;
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
public class SubmissionController {

	private final SubmissionService submissionService;

	private final RateLimiter rateLimiter;

	SubmissionController(SubmissionService submissionService, RateLimiter rateLimiter) {
		this.submissionService = submissionService;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/api/cohorts/{cohortId}/assignments")
	public List<AssignmentView> assignments(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId) {
		return this.submissionService.assignments(user, cohortId);
	}

	@GetMapping("/api/cohorts/{cohortId}/progress")
	public ProgressView progress(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId) {
		return this.submissionService.progress(user, cohortId);
	}

	@GetMapping("/api/cohorts/{cohortId}/assignments/{itemId}/submissions")
	public SubmissionList list(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId,
			@PathVariable UUID itemId) {
		return this.submissionService.list(user, cohortId, itemId);
	}

	@PutMapping("/api/cohorts/{cohortId}/assignments/{itemId}/my-submission")
	public SubmissionDetail saveMine(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId,
			@PathVariable UUID itemId, @Valid @RequestBody SaveSubmission request) {
		return this.submissionService.saveMine(user, cohortId, itemId, request.bodyMd(), request.version());
	}

	@PostMapping("/api/cohorts/{cohortId}/assignments/{itemId}/my-submission/submit")
	public SubmissionDetail submitMine(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId,
			@PathVariable UUID itemId) {
		return this.submissionService.submitMine(user, cohortId, itemId);
	}

	@GetMapping("/api/submissions/{submissionId}")
	public SubmissionDetail detail(@AuthenticationPrincipal SessionUser user, @PathVariable UUID submissionId) {
		return this.submissionService.detail(user, submissionId);
	}

	@PostMapping("/api/submissions/{submissionId}/comments")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentView comment(@AuthenticationPrincipal SessionUser user, @PathVariable UUID submissionId,
			@Valid @RequestBody NewComment request) {
		this.rateLimiter.consume(Plan.COMMENT_PER_USER, user.id().toString());
		return this.submissionService.comment(user, submissionId, request.bodyMd(), request.parentId());
	}

	@PostMapping("/api/submissions/{submissionId}/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public CommentView review(@AuthenticationPrincipal SessionUser user, @PathVariable UUID submissionId,
			@Valid @RequestBody NewReview request) {
		this.rateLimiter.consume(Plan.COMMENT_PER_USER, user.id().toString());
		return this.submissionService.review(user, submissionId, request.decision(), request.bodyMd());
	}

	@PatchMapping("/api/review-comments/{commentId}")
	public CommentView editComment(@AuthenticationPrincipal SessionUser user, @PathVariable UUID commentId,
			@Valid @RequestBody EditComment request) {
		return this.submissionService.editComment(user, commentId, request.bodyMd());
	}

	@DeleteMapping("/api/review-comments/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteComment(@AuthenticationPrincipal SessionUser user, @PathVariable UUID commentId) {
		this.submissionService.deleteComment(user, commentId);
	}

	public record SaveSubmission(@NotNull @Size(max = 100000) String bodyMd, Long version) {
	}

	public record NewComment(@NotBlank @Size(max = 20000) String bodyMd, UUID parentId) {
	}

	public record NewReview(@NotNull ReviewComment.Decision decision, @Size(max = 20000) String bodyMd) {
	}

	public record EditComment(@NotBlank @Size(max = 20000) String bodyMd) {
	}

}
