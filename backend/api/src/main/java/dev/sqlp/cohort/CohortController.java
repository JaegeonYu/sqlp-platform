package dev.sqlp.cohort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import dev.sqlp.auth.SessionUser;
import dev.sqlp.cohort.CohortService.CohortDetail;
import dev.sqlp.cohort.CohortService.CohortSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CohortController {

	private final CohortService cohortService;

	CohortController(CohortService cohortService) {
		this.cohortService = cohortService;
	}

	@PostMapping("/api/groups/{groupId}/cohorts")
	@ResponseStatus(HttpStatus.CREATED)
	public CohortSummary create(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@Valid @RequestBody NewCohort request) {
		return this.cohortService.create(user, groupId, request.courseId(), request.name(), request.startsOn(),
				request.meetingTime(), request.intervalDays());
	}

	@GetMapping("/api/groups/{groupId}/cohorts")
	public List<CohortSummary> list(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId) {
		return this.cohortService.listForGroup(user, groupId);
	}

	@GetMapping("/api/cohorts/{cohortId}")
	public CohortDetail detail(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId) {
		return this.cohortService.detail(user, cohortId);
	}

	@PatchMapping("/api/cohorts/{cohortId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void update(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId,
			@Valid @RequestBody CohortEdit request) {
		this.cohortService.update(user, cohortId, request.name(), request.status());
	}

	@PutMapping("/api/cohorts/{cohortId}/sessions/{chapterId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void updateSession(@AuthenticationPrincipal SessionUser user, @PathVariable UUID cohortId,
			@PathVariable UUID chapterId, @Valid @RequestBody SessionEdit request) {
		this.cohortService.updateSession(user, cohortId, chapterId, request.scheduledAt(), request.presenterId(),
				request.note());
	}

	public record NewCohort(@NotNull UUID courseId, @NotBlank @Size(max = 100) String name,
			@NotNull LocalDate startsOn, @NotNull LocalTime meetingTime, @Min(1) @Max(31) int intervalDays) {
	}

	public record CohortEdit(@NotBlank @Size(max = 100) String name, @NotNull Cohort.Status status) {
	}

	public record SessionEdit(Instant scheduledAt, UUID presenterId, @Size(max = 500) String note) {
	}

}
