package dev.sqlp.auth;

import dev.sqlp.ApiTestSupport;
import dev.sqlp.audit.AuditLogRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTests extends ApiTestSupport {

	@Autowired
	private AuditLogRepository auditLogs;

	@Test
	void signupRequiresAdminApprovalBeforeLogin() throws Exception {
		String email = uniqueEmail();
		signup(email);

		postJson("/api/auth/login", json("{'email':'%s','password':'%s'}", email, PASSWORD), null)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("PENDING_APPROVAL"));

		setStatus(email, "ACTIVE");
		Cookie session = login(email, PASSWORD);

		getAs("/api/auth/me", session).andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.systemRole").value("USER"));
	}

	@Test
	void sessionCookieIsHardened() throws Exception {
		String setCookie = postJson("/api/auth/login",
				json("{'email':'%s','password':'%s'}", ADMIN_EMAIL, ADMIN_PASSWORD), null)
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader("Set-Cookie");
		assertThat(setCookie).startsWith("SQLP_SESSION=").contains("HttpOnly", "Secure", "SameSite=Lax");
	}

	@Test
	void duplicateSignupLooksIdenticalToNewSignup() throws Exception {
		String email = uniqueEmail();
		signup(email);
		signup(email);
		assertThat(this.auditLogs.findByActionOrderByIdDesc("SIGNUP_DUPLICATE"))
			.anyMatch((log) -> email.equals(log.getTarget()));
	}

	@Test
	void unknownAccountAndWrongPasswordGiveSameError() throws Exception {
		String email = uniqueEmail();
		activeUser(email);

		postJson("/api/auth/login", json("{'email':'%s','password':'%s'}", uniqueEmail(), PASSWORD), null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		postJson("/api/auth/login", json("{'email':'%s','password':'wrong-password-1'}", email), null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void fiveFailuresLockTheAccountEvenForCorrectPassword() throws Exception {
		String email = uniqueEmail();
		activeUser(email);
		for (int i = 0; i < 5; i++) {
			postJson("/api/auth/login", json("{'email':'%s','password':'wrong-password-%d'}", email, i), null)
				.andExpect(status().isUnauthorized());
		}
		postJson("/api/auth/login", json("{'email':'%s','password':'%s'}", email, PASSWORD), null)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("{'email':'%s','password':'%s'}", ADMIN_EMAIL, ADMIN_PASSWORD)))
			.andExpect(status().isForbidden());
	}

	@Test
	void anonymousUserCannotReachProtectedApi() throws Exception {
		this.mvc.perform(get("/api/groups")).andExpect(status().isUnauthorized());
		this.mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void regularUserCannotReachAdminApi() throws Exception {
		Cookie session = activeUser(uniqueEmail());
		getAs("/api/admin/users", session).andExpect(status().isForbidden());
	}

	@Test
	void suspendedUserLosesExistingSession() throws Exception {
		String email = uniqueEmail();
		Cookie session = activeUser(email);
		getAs("/api/auth/me", session).andExpect(status().isOk());

		setStatus(email, "SUSPENDED");

		getAs("/api/auth/me", session).andExpect(status().isUnauthorized());
	}

	@Test
	void signupIsRateLimitedPerClientIp() throws Exception {
		for (int i = 0; i < 20; i++) {
			this.mvc.perform(post("/api/auth/signup").with(fromIp("10.9.9.9"))
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json("{'email':'%s','nickname':'tester','password':'%s'}", uniqueEmail(), PASSWORD)))
				.andExpect(status().isAccepted());
		}
		this.mvc.perform(post("/api/auth/signup").with(fromIp("10.9.9.9"))
			.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json("{'email':'%s','nickname':'tester','password':'%s'}", uniqueEmail(), PASSWORD)))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void adminCannotChangeOwnStatus() throws Exception {
		Cookie admin = adminSession();
		postJson("/api/admin/users/" + userIdOf(ADMIN_EMAIL, admin) + "/status", json("{'status':'SUSPENDED'}"),
				admin)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SELF_CHANGE"));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor fromIp(String ip) {
		return (request) -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

}
