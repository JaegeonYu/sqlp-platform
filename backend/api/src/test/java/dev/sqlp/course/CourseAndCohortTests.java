package dev.sqlp.course;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.sqlp.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseAndCohortTests extends ApiTestSupport {

	@Test
	void membersReadCourseButOnlyManagersEdit() throws Exception {
		Fixture f = fixture();
		String courseId = createCourse(f);
		String chapterId = addChapter(f.owner, courseId, "1장 인덱스 기본");

		getAs("/api/courses/" + courseId, f.member).andExpect(status().isOk())
			.andExpect(jsonPath("$.canEdit").value(false))
			.andExpect(jsonPath("$.chapters[0].title").value("1장 인덱스 기본"));
		getAs("/api/courses/" + courseId, f.owner).andExpect(jsonPath("$.canEdit").value(true));

		send(patch("/api/chapters/" + chapterId), "{'version':0,'title':'바꿈'}", f.member)
			.andExpect(status().isForbidden());
		postJson("/api/courses/" + courseId + "/chapters", json("{'title':'2장'}"), f.member)
			.andExpect(status().isForbidden());

		Cookie outsider = activeUser(uniqueEmail());
		getAs("/api/courses/" + courseId, outsider).andExpect(status().isNotFound());
		send(patch("/api/chapters/" + chapterId), "{'version':0,'title':'바꿈'}", outsider)
			.andExpect(status().isNotFound());
	}

	@Test
	void staleChapterEditIsRejectedAsConflict() throws Exception {
		Fixture f = fixture();
		String courseId = createCourse(f);
		String chapterId = addChapter(f.owner, courseId, "1장");

		send(patch("/api/chapters/" + chapterId), "{'version':0,'title':'1장','guideMd':'첫 저장'}", f.owner)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1));
		send(patch("/api/chapters/" + chapterId), "{'version':0,'title':'1장','guideMd':'덮어쓰기'}", f.owner)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EDIT_CONFLICT"));
	}

	@Test
	void chaptersCanBeReorderedAndAreRenumberedAfterDelete() throws Exception {
		Fixture f = fixture();
		String courseId = createCourse(f);
		String a = addChapter(f.owner, courseId, "A");
		String b = addChapter(f.owner, courseId, "B");
		String c = addChapter(f.owner, courseId, "C");

		send(put("/api/courses/" + courseId + "/chapter-order"),
				json("{'ids':['%s','%s','%s']}", c, a, b).replace('\'', '"'), f.owner)
			.andExpect(status().isNoContent());
		send(put("/api/courses/" + courseId + "/chapter-order"), json("{'ids':['%s']}", a), f.owner)
			.andExpect(status().isBadRequest());
		this.mvc.perform(delete("/api/chapters/" + a).with(csrf()).cookie(f.owner))
			.andExpect(status().isNoContent());

		String body = getAs("/api/courses/" + courseId, f.owner).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.chapters[*].title")).containsExactly("C", "B");
		assertThat(JsonPath.<List<Integer>>read(body, "$.chapters[*].position")).containsExactly(1, 2);
	}

	@Test
	void problemItemsAreNotAvailableYet() throws Exception {
		Fixture f = fixture();
		String chapterId = addChapter(f.owner, createCourse(f), "1장");
		postJson("/api/chapters/" + chapterId + "/items", json("{'type':'LAB','title':'실습 1'}"), f.owner)
			.andExpect(status().isCreated());
		postJson("/api/chapters/" + chapterId + "/items", json("{'type':'PROBLEM','title':'문제'}"), f.owner)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSUPPORTED_TYPE"));
	}

	@Test
	void cohortSchedulesOneSessionPerChapterInSeoulTime() throws Exception {
		Fixture f = fixture();
		String courseId = createCourse(f);
		addChapter(f.owner, courseId, "1장");
		addChapter(f.owner, courseId, "2장");

		String cohortId = createCohort(f.owner, f.groupId, courseId);

		getAs("/api/cohorts/" + cohortId, f.member).andExpect(status().isOk())
			.andExpect(jsonPath("$.canManage").value(false))
			.andExpect(jsonPath("$.course.bookTitle").value("친절한 SQL 튜닝"))
			.andExpect(jsonPath("$.schedule.length()").value(2))
			// 2026-11-02 20:00 KST = 11:00 UTC, 다음 장은 7일 뒤
			.andExpect(jsonPath("$.schedule[0].scheduledAt").value("2026-11-02T11:00:00Z"))
			.andExpect(jsonPath("$.schedule[1].scheduledAt").value("2026-11-09T11:00:00Z"));
	}

	@Test
	void presenterMustBeGroupMemberAndOnlyManagersSchedule() throws Exception {
		Fixture f = fixture();
		String courseId = createCourse(f);
		String chapterId = addChapter(f.owner, courseId, "1장");
		String cohortId = createCohort(f.owner, f.groupId, courseId);
		String memberId = JsonPath.read(getAs("/api/auth/me", f.member).andReturn().getResponse().getContentAsString(),
				"$.id");
		Cookie outsider = activeUser(uniqueEmail());
		String outsiderId = JsonPath.read(getAs("/api/auth/me", outsider).andReturn().getResponse().getContentAsString(),
				"$.id");
		String path = "/api/cohorts/" + cohortId + "/sessions/" + chapterId;

		send(put(path), json("{'presenterId':'%s'}", outsiderId), f.owner).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PRESENTER_NOT_MEMBER"));
		send(put(path), json("{'presenterId':'%s'}", memberId), f.member).andExpect(status().isForbidden());
		send(put(path), json("{'presenterId':'%s','scheduledAt':'2026-11-03T11:00:00Z'}", memberId), f.owner)
			.andExpect(status().isNoContent());
		getAs("/api/cohorts/" + cohortId, f.member).andExpect(jsonPath("$.schedule[0].presenterNickname").value("tester"));
		getAs("/api/cohorts/" + cohortId, outsider).andExpect(status().isNotFound());
	}

	@Test
	void cohortCannotUseAnotherGroupsCourse() throws Exception {
		Fixture mine = fixture();
		Fixture other = fixture();
		String othersCourse = createCourse(other);
		postJson("/api/groups/" + mine.groupId + "/cohorts", json(
				"{'courseId':'%s','name':'1기','startsOn':'2026-11-02','meetingTime':'20:00','intervalDays':7}",
				othersCourse), mine.owner)
			.andExpect(status().isNotFound());
	}

	// --- 도구 ---

	private record Fixture(Cookie owner, Cookie member, String groupId) {
	}

	/** OWNER 한 명과 MEMBER 한 명이 있는 그룹 */
	private Fixture fixture() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = JsonPath.read(postJson("/api/groups", json("{'name':'스터디'}"), owner).andReturn()
			.getResponse()
			.getContentAsString(), "$.id");
		String token = JsonPath.read(postJson("/api/groups/" + groupId + "/invites",
				json("{'expiresInDays':7,'maxUses':5}"), owner)
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.token");
		Cookie member = activeUser(uniqueEmail());
		postJson("/api/invites/accept", json("{'token':'%s'}", token), member).andExpect(status().isOk());
		return new Fixture(owner, member, groupId);
	}

	private String createCourse(Fixture f) throws Exception {
		String body = postJson("/api/groups/" + f.groupId + "/courses",
				json("{'book':{'title':'친절한 SQL 튜닝','author':'조시형'},'title':'SQL 튜닝 1기 코스'}"), f.owner)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String addChapter(Cookie session, String courseId, String title) throws Exception {
		String body = postJson("/api/courses/" + courseId + "/chapters", json("{'title':'%s'}", title), session)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createCohort(Cookie session, String groupId, String courseId) throws Exception {
		String body = postJson("/api/groups/" + groupId + "/cohorts", json(
				"{'courseId':'%s','name':'1기','startsOn':'2026-11-02','meetingTime':'20:00','intervalDays':7}",
				courseId), session)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String body, Cookie session) throws Exception {
		return this.mvc.perform(request.with(csrf())
			.cookie(session)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body.replace('\'', '"')));
	}

}
