package dev.sqlp;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.sqlp.security.RateLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 테스트 공통 도구. 세션은 Spring Session JDBC 쿠키(SQLP_SESSION)로 주고받는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

	protected static final String ADMIN_EMAIL = "admin@test.local";

	protected static final String ADMIN_PASSWORD = "admin-password-for-tests";

	protected static final String PASSWORD = "correct-horse-battery";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	private RateLimiter rateLimiter;

	@BeforeEach
	void resetRateLimits() {
		this.rateLimiter.reset();
	}

	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@test.local";
	}

	protected static String json(String template, Object... args) {
		return template.formatted(args).replace('\'', '"');
	}

	protected ResultActions postJson(String url, String body, Cookie session) throws Exception {
		MockHttpServletRequestBuilder request = post(url).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(body);
		if (session != null) {
			request.cookie(session);
		}
		return this.mvc.perform(request);
	}

	protected ResultActions getAs(String url, Cookie session) throws Exception {
		return this.mvc.perform(get(url).cookie(session));
	}

	protected void signup(String email) throws Exception {
		postJson("/api/auth/signup", json("{'email':'%s','nickname':'tester','password':'%s'}", email, PASSWORD), null)
			.andExpect(status().isAccepted());
	}

	protected Cookie login(String email, String password) throws Exception {
		MvcResult result = postJson("/api/auth/login", json("{'email':'%s','password':'%s'}", email, password), null)
			.andExpect(status().isOk())
			.andReturn();
		return result.getResponse().getCookie("SQLP_SESSION");
	}

	protected Cookie adminSession() throws Exception {
		return login(ADMIN_EMAIL, ADMIN_PASSWORD);
	}

	protected String userIdOf(String email, Cookie admin) throws Exception {
		String body = getAs("/api/admin/users", admin).andReturn().getResponse().getContentAsString();
		return JsonPath.<java.util.List<String>>read(body, "$[?(@.email == '" + email + "')].id").get(0);
	}

	protected void setStatus(String email, String status) throws Exception {
		Cookie admin = adminSession();
		postJson("/api/admin/users/" + userIdOf(email, admin) + "/status", json("{'status':'%s'}", status), admin)
			.andExpect(status().isOk());
	}

	/** 가입 → 관리자 승인 → 로그인한 세션 */
	protected Cookie activeUser(String email) throws Exception {
		signup(email);
		setStatus(email, "ACTIVE");
		return login(email, PASSWORD);
	}

}
