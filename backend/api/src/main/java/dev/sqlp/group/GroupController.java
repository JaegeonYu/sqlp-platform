package dev.sqlp.group;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.sqlp.auth.SessionUser;
import dev.sqlp.group.GroupService.GroupView;
import dev.sqlp.group.GroupService.InviteCreated;
import dev.sqlp.group.GroupService.InvitePreview;
import dev.sqlp.group.GroupService.InviteView;
import dev.sqlp.group.GroupService.MemberView;
import dev.sqlp.security.RateLimiter;
import dev.sqlp.security.RateLimiter.Plan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupController {

	private final GroupService groupService;

	private final RateLimiter rateLimiter;

	GroupController(GroupService groupService, RateLimiter rateLimiter) {
		this.groupService = groupService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/api/groups")
	@ResponseStatus(HttpStatus.CREATED)
	public GroupView create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody CreateGroup request) {
		return this.groupService.create(user, request.name(), request.description());
	}

	@GetMapping("/api/groups")
	public List<GroupView> myGroups(@AuthenticationPrincipal SessionUser user) {
		return this.groupService.myGroups(user);
	}

	@GetMapping("/api/groups/{groupId}")
	public GroupView detail(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId) {
		return this.groupService.detail(user, groupId);
	}

	@GetMapping("/api/groups/{groupId}/members")
	public List<MemberView> members(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId) {
		return this.groupService.members(user, groupId);
	}

	@PatchMapping("/api/groups/{groupId}/members/{userId}")
	public MemberView changeRole(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@PathVariable UUID userId, @Valid @RequestBody ChangeRole request) {
		return this.groupService.changeRole(user, groupId, userId, request.role());
	}

	@DeleteMapping("/api/groups/{groupId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@PathVariable UUID userId) {
		this.groupService.removeMember(user, groupId, userId);
	}

	@PostMapping("/api/groups/{groupId}/invites")
	@ResponseStatus(HttpStatus.CREATED)
	public InviteCreated createInvite(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@Valid @RequestBody CreateInvite request) {
		return this.groupService.createInvite(user, groupId, request.expiresInDays(), request.maxUses());
	}

	@GetMapping("/api/groups/{groupId}/invites")
	public List<InviteView> invites(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId) {
		return this.groupService.invites(user, groupId);
	}

	@DeleteMapping("/api/groups/{groupId}/invites/{inviteId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revokeInvite(@AuthenticationPrincipal SessionUser user, @PathVariable UUID groupId,
			@PathVariable UUID inviteId) {
		this.groupService.revokeInvite(user, groupId, inviteId);
	}

	/** 토큰은 URL 경로가 아니라 본문으로 받는다(접근 로그에 남지 않도록) */
	@PostMapping("/api/invites/preview")
	public InvitePreview previewInvite(@AuthenticationPrincipal SessionUser user,
			@Valid @RequestBody InviteToken request) {
		this.rateLimiter.consume(Plan.INVITE_PER_USER, user.id().toString());
		return this.groupService.previewInvite(user, request.token());
	}

	@PostMapping("/api/invites/accept")
	public Map<String, UUID> acceptInvite(@AuthenticationPrincipal SessionUser user,
			@Valid @RequestBody InviteToken request) {
		this.rateLimiter.consume(Plan.INVITE_PER_USER, user.id().toString());
		return Map.of("groupId", this.groupService.acceptInvite(user, request.token()));
	}

	public record CreateGroup(@NotBlank @Size(max = 60) String name, @Size(max = 500) String description) {
	}

	public record ChangeRole(@NotNull GroupRole role) {
	}

	public record CreateInvite(@Min(1) @Max(30) int expiresInDays, @Min(1) @Max(100) int maxUses) {
	}

	public record InviteToken(@NotBlank @Size(max = 100) String token) {
	}

}
