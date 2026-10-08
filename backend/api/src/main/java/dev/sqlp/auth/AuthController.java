package dev.sqlp.auth;

import java.util.Map;

import dev.sqlp.security.RateLimiter;
import dev.sqlp.security.RateLimiter.Plan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final RateLimiter rateLimiter;

	private final SecurityContextRepository securityContextRepository;

	private final AuthProperties properties;

	public AuthController(AuthService authService, RateLimiter rateLimiter,
			SecurityContextRepository securityContextRepository, AuthProperties properties) {
		this.authService = authService;
		this.rateLimiter = rateLimiter;
		this.securityContextRepository = securityContextRepository;
		this.properties = properties;
	}

	/** SPA가 처음 뜰 때 호출해 XSRF-TOKEN 쿠키를 받는다 */
	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName());
	}

	@GetMapping("/providers")
	public Map<String, Boolean> providers() {
		return Map.of("google", this.properties.googleEnabled());
	}

	@GetMapping("/me")
	public SessionUser me(@AuthenticationPrincipal SessionUser user) {
		return user;
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, String> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
		this.rateLimiter.consume(Plan.AUTH_PER_IP, http.getRemoteAddr());
		this.authService.signup(request.email(), request.nickname(), request.password(), request.note());
		return Map.of("message", "가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
	}

	@PostMapping("/login")
	public SessionUser login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
			HttpServletResponse response) {
		this.rateLimiter.consume(Plan.AUTH_PER_IP, http.getRemoteAddr());
		this.rateLimiter.consume(Plan.LOGIN_PER_EMAIL, AuthService.normalizeEmail(request.email()));
		SessionUser user = this.authService.authenticate(request.email(), request.password());
		startSession(user, http, response, this.securityContextRepository);
		return user;
	}

	/**
	 * 세션 고정 공격을 막기 위해 기존 세션을 버리고 새 세션에 인증 정보를 저장한다.
	 */
	static void startSession(SessionUser user, HttpServletRequest request, HttpServletResponse response,
			SecurityContextRepository repository) {
		HttpSession old = request.getSession(false);
		if (old != null) {
			old.invalidate();
		}
		request.getSession(true);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(user.toAuthentication());
		SecurityContextHolder.setContext(context);
		repository.saveContext(context, request, response);
	}

	public record SignupRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 2, max = 40) String nickname, @NotBlank @Size(min = 10, max = 128) String password,
			@Size(max = 500) String note) {
	}

	public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {
	}

}
