package dev.sqlp.security;

import java.io.IOException;
import java.util.Optional;

import dev.sqlp.auth.AppUser;
import dev.sqlp.auth.AppUserRepository;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.auth.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 세션 사용자의 현재 상태를 DB에서 다시 확인한다.
 * 관리자가 정지·거절하면 다음 요청부터 세션이 끊기고, 역할이 바뀌면 권한이 즉시 반영된다.
 * Spring Boot가 일반 서블릿 필터로 자동 등록하지 않도록 빈이 아닌 보안 필터 체인 안에서만 생성한다.
 */
class ActiveUserFilter extends OncePerRequestFilter {

	private final AppUserRepository users;

	private final SecurityContextRepository securityContextRepository;

	ActiveUserFilter(AppUserRepository users, SecurityContextRepository securityContextRepository) {
		this.users = users;
		this.securityContextRepository = securityContextRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof SessionUser sessionUser) {
			Optional<AppUser> current = this.users.findById(sessionUser.id());
			if (current.isEmpty() || current.get().getStatus() != UserStatus.ACTIVE) {
				SecurityContextHolder.clearContext();
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.invalidate();
				}
			}
			else {
				SessionUser refreshed = current.get().toSessionUser();
				if (!refreshed.equals(sessionUser)) {
					SecurityContext context = SecurityContextHolder.createEmptyContext();
					context.setAuthentication(refreshed.toAuthentication());
					SecurityContextHolder.setContext(context);
					this.securityContextRepository.saveContext(context, request, response);
				}
			}
		}
		chain.doFilter(request, response);
	}

}
