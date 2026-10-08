package dev.sqlp.submission;

import com.jayway.jsonpath.JsonPath;
import dev.sqlp.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionTests extends ApiTestSupport {

	@Test
	void othersSubmissionsStayHiddenUntilISubmit() throws Exception {
		Fixture f = fixture("2099-01-05");
		String aId = save(f, f.alice, "A의 답: 인덱스 범위 스캔");

		// 임시 저장은 작성자만 본다
		getAs("/api/submissions/" + aId, f.bob).andExpect(status().isNotFound());
		submit(f, f.alice);

		getAs(f.itemPath() + "/submissions", f.bob).andExpect(jsonPath("$.canViewOthers").value(false))
			.andExpect(jsonPath("$.others.length()").value(0));
		getAs("/api/submissions/" + aId, f.bob).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("SUBMIT_FIRST"));
		// 운영진은 항상 본다
		getAs("/api/submissions/" + aId, f.owner).andExpect(status().isOk());

		save(f, f.bob, "B의 답");
		submit(f, f.bob);
		getAs("/api/submissions/" + aId, f.bob).andExpect(status().isOk())
			.andExpect(jsonPath("$.bodyMd").value("A의 답: 인덱스 범위 스캔"))
			.andExpect(jsonPath("$.canReview").value(true));
	}

	@Test
	void pastDueSubmissionsAreVisibleWithoutSubmitting() throws Exception {
		Fixture f = fixture("2020-01-06");
		String aId = save(f, f.alice, "지난 과제");
		submit(f, f.alice);
		getAs("/api/submissions/" + aId, f.bob).andExpect(status().isOk());
		getAs(f.itemPath() + "/submissions", f.alice).andExpect(jsonPath("$.mine.late").value(true));
	}

	@Test
	void reviewCycleEndsWithLockedApproval() throws Exception {
		Fixture f = fixture("2099-01-05");
		String aId = save(f, f.alice, "초안");
		submit(f, f.alice);

		postJson("/api/submissions/" + aId + "/reviews", json("{'decision':'APPROVE'}"), f.alice)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELF_REVIEW"));
		postJson("/api/submissions/" + aId + "/reviews",
				json("{'decision':'REQUEST_CHANGES','bodyMd':'실행계획을 첨부하세요'}"), f.owner)
			.andExpect(status().isCreated());
		getAs("/api/submissions/" + aId, f.alice).andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
			.andExpect(jsonPath("$.comments[0].decision").value("REQUEST_CHANGES"));

		long version = ((Number) JsonPath.read(getAs("/api/submissions/" + aId, f.alice).andReturn()
			.getResponse()
			.getContentAsString(), "$.version")).longValue();
		send(put(f.itemPath() + "/my-submission"), json("{'bodyMd':'수정본','version':%d}", version), f.alice)
			.andExpect(status().isOk());
		// 같은 버전으로 다시 저장하면 다른 탭의 수정을 덮어쓰지 않도록 거절한다
		send(put(f.itemPath() + "/my-submission"), json("{'bodyMd':'다른 탭','version':%d}", version), f.alice)
			.andExpect(status().isConflict());
		submit(f, f.alice);
		postJson("/api/submissions/" + aId + "/reviews", json("{'decision':'APPROVE'}"), f.owner)
			.andExpect(status().isCreated());

		getAs("/api/submissions/" + aId, f.alice).andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.canEdit").value(false));
		send(put(f.itemPath() + "/my-submission"), json("{'bodyMd':'승인 후 수정','version':%d}", version + 2),
				f.alice)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("ALREADY_APPROVED"));
	}

	@Test
	void progressShowsStatusPerMemberAndOutsidersSeeNothing() throws Exception {
		Fixture f = fixture("2099-01-05");
		save(f, f.alice, "답");
		submit(f, f.alice);

		getAs("/api/cohorts/" + f.cohortId + "/progress", f.bob).andExpect(status().isOk())
			.andExpect(jsonPath("$.assignments[0].title").value("실행계획 분석"))
			.andExpect(jsonPath("$.members.length()").value(3))
			.andExpect(jsonPath("$.members[?(@.nickname == 'alice')].cells[0].status").value("SUBMITTED"))
			.andExpect(jsonPath("$.members[?(@.nickname == 'bob')].cells[0].status")
				.value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

		Cookie outsider = activeUser(uniqueEmail());
		getAs("/api/cohorts/" + f.cohortId + "/progress", outsider).andExpect(status().isNotFound());
		getAs("/api/cohorts/" + f.cohortId + "/assignments", outsider).andExpect(status().isNotFound());
	}

	@Test
	void commentsCanBeRemovedByAuthorOrManagerOnly() throws Exception {
		Fixture f = fixture("2020-01-06");
		String aId = save(f, f.alice, "답");
		submit(f, f.alice);
		String commentId = JsonPath.read(postJson("/api/submissions/" + aId + "/comments",
				json("{'bodyMd':'좋네요'}"), f.bob)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.id");

		this.mvc.perform(delete("/api/review-comments/" + commentId).with(csrf()).cookie(f.alice))
			.andExpect(status().isForbidden());
		this.mvc.perform(delete("/api/review-comments/" + commentId).with(csrf()).cookie(f.owner))
			.andExpect(status().isNoContent());
		getAs("/api/submissions/" + aId, f.alice).andExpect(jsonPath("$.comments[0].deleted").value(true))
			.andExpect(jsonPath("$.comments[0].bodyMd").value(""));
	}

	// --- 도구 ---

	private record Fixture(Cookie owner, Cookie alice, Cookie bob, String cohortId, String itemId) {

		String itemPath() {
			return "/api/cohorts/" + this.cohortId + "/assignments/" + this.itemId;
		}

	}

	/** OWNER + 멤버 alice, bob. 장 1개에 과제 1개, 첫 모임(=마감)은 startsOn 20:00 KST */
	private Fixture fixture(String startsOn) throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = read(postJson("/api/groups", json("{'name':'스터디'}"), owner), "$.id");
		String token = read(postJson("/api/groups/" + groupId + "/invites", json("{'expiresInDays':7,'maxUses':5}"),
				owner), "$.token");
		Cookie alice = member(token, "alice");
		Cookie bob = member(token, "bob");
		String courseId = read(postJson("/api/groups/" + groupId + "/courses",
				json("{'book':{'title':'친절한 SQL 튜닝'},'title':'코스'}"), owner), "$.id");
		String chapterId = read(postJson("/api/courses/" + courseId + "/chapters", json("{'title':'1장'}"), owner),
				"$.id");
		String itemId = read(postJson("/api/chapters/" + chapterId + "/items",
				json("{'type':'ASSIGNMENT','title':'실행계획 분석'}"), owner), "$.id");
		String cohortId = read(postJson("/api/groups/" + groupId + "/cohorts", json(
				"{'courseId':'%s','name':'1기','startsOn':'%s','meetingTime':'20:00','intervalDays':7}", courseId,
				startsOn), owner), "$.id");
		return new Fixture(owner, alice, bob, cohortId, itemId);
	}

	private Cookie member(String token, String nickname) throws Exception {
		String email = uniqueEmail();
		postJson("/api/auth/signup",
				json("{'email':'%s','nickname':'%s','password':'%s'}", email, nickname, PASSWORD), null)
			.andExpect(status().isAccepted());
		setStatus(email, "ACTIVE");
		Cookie session = login(email, PASSWORD);
		postJson("/api/invites/accept", json("{'token':'%s'}", token), session).andExpect(status().isOk());
		return session;
	}

	private String save(Fixture f, Cookie who, String body) throws Exception {
		return read(send(put(f.itemPath() + "/my-submission"), json("{'bodyMd':'%s'}", body), who)
			.andExpect(status().isOk()), "$.id");
	}

	private void submit(Fixture f, Cookie who) throws Exception {
		postJson(f.itemPath() + "/my-submission/submit", "{}", who).andExpect(status().isOk());
	}

	private static String read(ResultActions result, String path) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
			String body, Cookie session) throws Exception {
		return this.mvc
			.perform(request.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
