package dev.sqlp.group;

import com.jayway.jsonpath.JsonPath;
import dev.sqlp.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupTests extends ApiTestSupport {

	@Test
	void creatorBecomesOwnerAndOutsidersSeeNotFound() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		Cookie outsider = activeUser(uniqueEmail());
		String groupId = createGroup(owner);

		getAs("/api/groups/" + groupId, owner).andExpect(status().isOk())
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andExpect(jsonPath("$.memberCount").value(1));
		getAs("/api/groups/" + groupId, outsider).andExpect(status().isNotFound());
		getAs("/api/groups/" + groupId + "/members", outsider).andExpect(status().isNotFound());
	}

	@Test
	void inviteLinkAddsMemberUntilMaxUses() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = createGroup(owner);
		String token = createInvite(owner, groupId, 1);

		Cookie first = activeUser(uniqueEmail());
		postJson("/api/invites/preview", json("{'token':'%s'}", token), first).andExpect(status().isOk())
			.andExpect(jsonPath("$.groupName").value("SQLP 1기"))
			.andExpect(jsonPath("$.alreadyMember").value(false));
		postJson("/api/invites/accept", json("{'token':'%s'}", token), first).andExpect(status().isOk());
		getAs("/api/groups/" + groupId, first).andExpect(jsonPath("$.myRole").value("MEMBER"));

		Cookie second = activeUser(uniqueEmail());
		postJson("/api/invites/accept", json("{'token':'%s'}", token), second).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("INVITE_INVALID"));
	}

	@Test
	void revokedOrForgedInviteIsRejected() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = createGroup(owner);
		String body = postJson("/api/groups/" + groupId + "/invites", json("{'expiresInDays':7,'maxUses':10}"), owner)
			.andReturn()
			.getResponse()
			.getContentAsString();
		String token = JsonPath.read(body, "$.token");
		String inviteId = JsonPath.read(body, "$.invite.id");

		this.mvc.perform(delete("/api/groups/" + groupId + "/invites/" + inviteId).with(csrf()).cookie(owner))
			.andExpect(status().isNoContent());

		Cookie user = activeUser(uniqueEmail());
		postJson("/api/invites/accept", json("{'token':'%s'}", token), user).andExpect(status().isNotFound());
		postJson("/api/invites/accept", json("{'token':'forged-token'}"), user).andExpect(status().isNotFound());
	}

	@Test
	void memberCannotCreateInvitesOrManageOthers() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = createGroup(owner);
		String memberEmail = uniqueEmail();
		Cookie member = activeUser(memberEmail);
		postJson("/api/invites/accept", json("{'token':'%s'}", createInvite(owner, groupId, 5)), member);

		postJson("/api/groups/" + groupId + "/invites", json("{'expiresInDays':7,'maxUses':10}"), member)
			.andExpect(status().isForbidden());
		String ownerId = memberIdOf(groupId, owner, "OWNER");
		this.mvc
			.perform(patch("/api/groups/" + groupId + "/members/" + ownerId).with(csrf())
				.cookie(member)
				.contentType("application/json")
				.content(json("{'role':'MEMBER'}")))
			.andExpect(status().isForbidden());
		this.mvc.perform(delete("/api/groups/" + groupId + "/members/" + ownerId).with(csrf()).cookie(member))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerPromotesManagerButCannotLeave() throws Exception {
		Cookie owner = activeUser(uniqueEmail());
		String groupId = createGroup(owner);
		Cookie member = activeUser(uniqueEmail());
		postJson("/api/invites/accept", json("{'token':'%s'}", createInvite(owner, groupId, 5)), member);
		String memberId = memberIdOf(groupId, owner, "MEMBER");

		this.mvc
			.perform(patch("/api/groups/" + groupId + "/members/" + memberId).with(csrf())
				.cookie(owner)
				.contentType("application/json")
				.content(json("{'role':'MANAGER'}")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("MANAGER"));
		postJson("/api/groups/" + groupId + "/invites", json("{'expiresInDays':1,'maxUses':1}"), member)
			.andExpect(status().isCreated());

		String ownerId = memberIdOf(groupId, owner, "OWNER");
		this.mvc.perform(delete("/api/groups/" + groupId + "/members/" + ownerId).with(csrf()).cookie(owner))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("OWNER_CANNOT_LEAVE"));
	}

	private String createGroup(Cookie owner) throws Exception {
		String body = postJson("/api/groups", json("{'name':'SQLP 1기','description':'친절한 SQL 튜닝'}"), owner)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String createInvite(Cookie session, String groupId, int maxUses) throws Exception {
		String body = postJson("/api/groups/" + groupId + "/invites",
				json("{'expiresInDays':7,'maxUses':%d}", maxUses), session)
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String memberIdOf(String groupId, Cookie session, String role) throws Exception {
		String body = getAs("/api/groups/" + groupId + "/members", session).andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.<java.util.List<String>>read(body, "$[?(@.role == '" + role + "')].userId").get(0);
	}

}
