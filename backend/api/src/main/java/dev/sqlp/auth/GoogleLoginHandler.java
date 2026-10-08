package dev.sqlp.auth;

import java.io.IOException;

import dev.sqlp.auth.AuthService.GoogleLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Google 로그인 콜백 처리. OAuth2 토큰은 세션에 남기지 않고, 승인된 사용자만 SessionUser로 새 세션을 만든다.
 */
@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	private final AuthService authService;

	private final SecurityContextRepository securityContextRepository;

	public GoogleLoginHandler(AuthService authService, SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.securityContextRepository = securityContextRepository;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		if (!(authentication.getPrincipal() instanceof OidcUser oidc)) {
			clearSession(request);
			response.sendRedirect("/login?result=OAUTH_FAILED");
			return;
		}
		GoogleLoginResult result = this.authService.loginWithGoogle(oidc.getSubject(), oidc.getEmail(),
				Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getFullName());
		if (result.outcome() == GoogleLoginResult.Outcome.ACTIVE) {
			AuthController.startSession(result.user(), request, response, this.securityContextRepository);
			response.sendRedirect("/");
			return;
		}
		clearSession(request);
		response.sendRedirect((result.outcome() == GoogleLoginResult.Outcome.PENDING) ? "/signup/pending"
				: "/login?result=" + result.outcome().name());
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		clearSession(request);
		response.sendRedirect("/login?result=OAUTH_FAILED");
	}

	private static void clearSession(HttpServletRequest request) {
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

}
