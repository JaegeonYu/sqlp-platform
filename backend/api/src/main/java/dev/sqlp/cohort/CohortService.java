package dev.sqlp.cohort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.sqlp.audit.AuditService;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.common.ApiException;
import dev.sqlp.course.Chapter;
import dev.sqlp.course.CourseService;
import dev.sqlp.course.CourseService.CourseRef;
import dev.sqlp.course.CourseVersion;
import dev.sqlp.group.GroupAccess;
import dev.sqlp.group.GroupRole;
import dev.sqlp.group.GroupService;
import dev.sqlp.group.GroupService.MemberView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기수 권한 규칙: 조회는 그룹 멤버, 생성·수정·일정 변경은 MANAGER 이상.
 * 발표자는 그 그룹의 멤버만 지정할 수 있다.
 */
@Service
@Transactional(readOnly = true)
public class CohortService {

	/** 일정 자동 생성과 표시 기준 시간대. 소규모 국내 스터디를 전제로 한다 */
	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final CohortRepository cohorts;

	private final CohortSessionRepository sessions;

	private final CourseService courseService;

	private final GroupService groupService;

	private final GroupAccess groupAccess;

	private final AuditService audit;

	CohortService(CohortRepository cohorts, CohortSessionRepository sessions, CourseService courseService,
			GroupService groupService, GroupAccess groupAccess, AuditService audit) {
		this.cohorts = cohorts;
		this.sessions = sessions;
		this.courseService = courseService;
		this.groupService = groupService;
		this.groupAccess = groupAccess;
		this.audit = audit;
	}

	/**
	 * 기수를 만들고, 코스의 장마다 startsOn부터 intervalDays 간격으로 일정을 만든다.
	 */
	@Transactional
	public CohortSummary create(SessionUser actor, UUID groupId, UUID courseId, String name, LocalDate startsOn,
			LocalTime meetingTime, int intervalDays) {
		this.groupAccess.require(actor, groupId, GroupRole.MANAGER);
		CourseVersion version = this.courseService.versionForCohort(groupId, courseId);
		Cohort cohort = this.cohorts
			.save(new Cohort(groupId, version.getId(), name.strip(), startsOn, actor.id()));
		List<Chapter> chapters = this.courseService.chaptersOf(version.getId());
		for (int i = 0; i < chapters.size(); i++) {
			Instant at = startsOn.plusDays((long) i * intervalDays).atTime(meetingTime).atZone(ZONE).toInstant();
			this.sessions.save(new CohortSession(cohort.getId(), chapters.get(i).getId(), at));
		}
		this.audit.record(actor.id(), "COHORT_CREATED", cohort.getId(), cohort.getName());
		return CohortSummary.of(cohort, this.courseService.courseRef(version.getId()));
	}

	public List<CohortSummary> listForGroup(SessionUser actor, UUID groupId) {
		this.groupAccess.require(actor, groupId, GroupRole.MEMBER);
		return this.cohorts.findByGroupIdOrderByStartsOnDesc(groupId)
			.stream()
			.map((c) -> CohortSummary.of(c, this.courseService.courseRef(c.getCourseVersionId())))
			.toList();
	}

	public CohortDetail detail(SessionUser actor, UUID cohortId) {
		Cohort cohort = this.cohorts.findById(cohortId).orElseThrow(ApiException::notFound);
		GroupRole role = this.groupAccess.require(actor, cohort.getGroupId(), GroupRole.MEMBER);
		List<MemberView> members = this.groupService.members(actor, cohort.getGroupId());
		Map<UUID, String> nicknames = members.stream()
			.collect(Collectors.toMap(MemberView::userId, MemberView::nickname));
		Map<UUID, CohortSession> byChapter = this.sessions.findByCohortId(cohortId)
			.stream()
			.collect(Collectors.toMap(CohortSession::getChapterId, Function.identity()));
		List<SessionView> schedule = this.courseService.chaptersOf(cohort.getCourseVersionId())
			.stream()
			.map((chapter) -> SessionView.of(chapter, byChapter.get(chapter.getId()), nicknames))
			.toList();
		return new CohortDetail(cohort.getId(), cohort.getGroupId(), cohort.getName(), cohort.getStartsOn(),
				cohort.getStatus(), this.courseService.courseRef(cohort.getCourseVersionId()),
				role.atLeast(GroupRole.MANAGER), schedule, members);
	}

	@Transactional
	public void update(SessionUser actor, UUID cohortId, String name, Cohort.Status status) {
		Cohort cohort = this.cohorts.findById(cohortId).orElseThrow(ApiException::notFound);
		this.groupAccess.require(actor, cohort.getGroupId(), GroupRole.MANAGER);
		Cohort.Status before = cohort.getStatus();
		cohort.update(name.strip(), status);
		if (before != status) {
			this.audit.record(actor.id(), "COHORT_STATUS_CHANGED", cohortId, before + " -> " + status);
		}
	}

	@Transactional
	public void updateSession(SessionUser actor, UUID cohortId, UUID chapterId, Instant scheduledAt,
			UUID presenterId, String note) {
		Cohort cohort = this.cohorts.findById(cohortId).orElseThrow(ApiException::notFound);
		this.groupAccess.require(actor, cohort.getGroupId(), GroupRole.MANAGER);
		boolean chapterInCourse = this.courseService.chaptersOf(cohort.getCourseVersionId())
			.stream()
			.anyMatch((c) -> c.getId().equals(chapterId));
		if (!chapterInCourse) {
			throw ApiException.notFound();
		}
		if (presenterId != null && !this.groupAccess.isMember(cohort.getGroupId(), presenterId)) {
			throw ApiException.badRequest("PRESENTER_NOT_MEMBER", "발표자는 그룹 멤버여야 합니다.");
		}
		CohortSession session = this.sessions.findByCohortIdAndChapterId(cohortId, chapterId)
			.orElseGet(() -> this.sessions.save(new CohortSession(cohortId, chapterId, null)));
		session.update(scheduledAt, presenterId, (note == null || note.isBlank()) ? null : note.strip());
	}

	public record CohortSummary(UUID id, String name, LocalDate startsOn, Cohort.Status status, CourseRef course) {

		static CohortSummary of(Cohort cohort, CourseRef course) {
			return new CohortSummary(cohort.getId(), cohort.getName(), cohort.getStartsOn(), cohort.getStatus(),
					course);
		}

	}

	public record SessionView(UUID chapterId, int position, String chapterTitle, Instant scheduledAt,
			UUID presenterId, String presenterNickname, String note) {

		static SessionView of(Chapter chapter, CohortSession session, Map<UUID, String> nicknames) {
			if (session == null) {
				return new SessionView(chapter.getId(), chapter.getPosition(), chapter.getTitle(), null, null, null,
						null);
			}
			return new SessionView(chapter.getId(), chapter.getPosition(), chapter.getTitle(),
					session.getScheduledAt(), session.getPresenterId(), nicknames.get(session.getPresenterId()),
					session.getNote());
		}

	}

	public record CohortDetail(UUID id, UUID groupId, String name, LocalDate startsOn, Cohort.Status status,
			CourseRef course, boolean canManage, List<SessionView> schedule, List<MemberView> members) {
	}

}
