package dev.sqlp.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.sqlp.audit.AuditService;
import dev.sqlp.auth.AppUser;
import dev.sqlp.auth.AppUserRepository;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.auth.SystemRole;
import dev.sqlp.auth.UserStatus;
import dev.sqlp.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시스템 관리자 전용: 가입 승인·거절, 계정 정지·복구. 접근 제어는 SecurityConfig의 /api/admin/** 규칙이 맡는다.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

	private final AppUserRepository users;

	private final AuditService audit;

	public AdminUserController(AppUserRepository users, AuditService audit) {
		this.users = users;
		this.audit = audit;
	}

	@GetMapping
	public List<AdminUserView> list(@RequestParam(required = false) UserStatus status) {
		List<AppUser> found = (status != null) ? this.users.findByStatusOrderByCreatedAtAsc(status)
				: this.users.findAllByOrderByCreatedAtAsc();
		return found.stream().map(AdminUserView::from).toList();
	}

	@PostMapping("/{id}/status")
	@Transactional
	public AdminUserView changeStatus(@AuthenticationPrincipal SessionUser admin, @PathVariable UUID id,
			@Valid @RequestBody StatusChange request) {
		if (request.status() == UserStatus.PENDING) {
			throw ApiException.badRequest("INVALID_STATUS", "승인 대기 상태로는 되돌릴 수 없습니다.");
		}
		if (admin.id().equals(id)) {
			throw ApiException.badRequest("SELF_CHANGE", "자기 자신의 상태는 바꿀 수 없습니다.");
		}
		AppUser user = this.users.findById(id).orElseThrow(ApiException::notFound);
		UserStatus before = user.getStatus();
		user.review(request.status(), admin.id());
		this.audit.record(admin.id(), "USER_STATUS_CHANGED", user.getId(), before + " -> " + request.status());
		return AdminUserView.from(user);
	}

	public record StatusChange(@NotNull UserStatus status) {
	}

	public record AdminUserView(UUID id, String email, String nickname, UserStatus status, SystemRole systemRole,
			String loginMethod, String signupNote, Instant createdAt, Instant reviewedAt) {

		static AdminUserView from(AppUser user) {
			return new AdminUserView(user.getId(), user.getEmail(), user.getNickname(), user.getStatus(),
					user.getSystemRole(), user.hasPassword() ? "EMAIL" : "GOOGLE", user.getSignupNote(),
					user.getCreatedAt(), user.getReviewedAt());
		}

	}

}
