package dev.sqlp.group;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.sqlp.audit.AuditService;
import dev.sqlp.auth.AppUser;
import dev.sqlp.auth.AppUserRepository;
import dev.sqlp.auth.SessionUser;
import dev.sqlp.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 권한 규칙
 * <ul>
 * <li>멤버가 아니면 그룹이 없는 것처럼 404를 준다(존재 여부 비노출)</li>
 * <li>역할 변경: OWNER만, 자기 자신은 불가, OWNER로 승격 불가(OWNER는 한 명)</li>
 * <li>내보내기: OWNER는 누구나(자신 제외), MANAGER는 MEMBER만. 본인 탈퇴는 OWNER만 불가</li>
 * <li>초대 링크 생성·조회·폐기: MANAGER 이상</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GroupService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final StudyGroupRepository groups;

	private final MembershipRepository memberships;

	private final GroupInviteRepository invites;

	private final AppUserRepository users;

	private final AuditService audit;

	GroupService(StudyGroupRepository groups, MembershipRepository memberships, GroupInviteRepository invites,
			AppUserRepository users, AuditService audit) {
		this.groups = groups;
		this.memberships = memberships;
		this.invites = invites;
		this.users = users;
		this.audit = audit;
	}

	@Transactional
	public GroupView create(SessionUser actor, String name, String description) {
		StudyGroup group = this.groups.save(new StudyGroup(name.strip(), blankToNull(description), actor.id()));
		this.memberships.save(new Membership(group.getId(), actor.id(), GroupRole.OWNER));
		this.audit.record(actor.id(), "GROUP_CREATED", group.getId(), group.getName());
		return GroupView.of(group, GroupRole.OWNER, 1);
	}

	public List<GroupView> myGroups(SessionUser actor) {
		List<Membership> mine = this.memberships.findByUserId(actor.id());
		Map<UUID, StudyGroup> byId = this.groups
			.findAllById(mine.stream().map((m) -> m.getId().groupId()).toList())
			.stream()
			.collect(Collectors.toMap(StudyGroup::getId, Function.identity()));
		return mine.stream()
			.map((m) -> GroupView.of(byId.get(m.getId().groupId()), m.getRole(),
					this.memberships.countByGroupId(m.getId().groupId())))
			.toList();
	}

	public GroupView detail(SessionUser actor, UUID groupId) {
		Membership mine = require(actor, groupId, GroupRole.MEMBER);
		StudyGroup group = this.groups.findById(groupId).orElseThrow(ApiException::notFound);
		return GroupView.of(group, mine.getRole(), this.memberships.countByGroupId(groupId));
	}

	public List<MemberView> members(SessionUser actor, UUID groupId) {
		require(actor, groupId, GroupRole.MEMBER);
		List<Membership> all = this.memberships.findByGroupId(groupId);
		Map<UUID, AppUser> byId = this.users.findAllById(all.stream().map(Membership::getUserId).toList())
			.stream()
			.collect(Collectors.toMap(AppUser::getId, Function.identity()));
		return all.stream()
			.map((m) -> new MemberView(m.getUserId(), byId.get(m.getUserId()).getNickname(), m.getRole(),
					m.getJoinedAt()))
			.toList();
	}

	@Transactional
	public MemberView changeRole(SessionUser actor, UUID groupId, UUID targetUserId, GroupRole newRole) {
		require(actor, groupId, GroupRole.OWNER);
		if (actor.id().equals(targetUserId)) {
			throw ApiException.badRequest("SELF_CHANGE", "자기 자신의 역할은 바꿀 수 없습니다.");
		}
		if (newRole == GroupRole.OWNER) {
			throw ApiException.badRequest("INVALID_ROLE", "OWNER는 그룹당 한 명입니다.");
		}
		Membership target = this.memberships.findById(new Membership.Key(groupId, targetUserId))
			.orElseThrow(ApiException::notFound);
		GroupRole before = target.getRole();
		target.changeRole(newRole);
		this.audit.record(actor.id(), "GROUP_ROLE_CHANGED", groupId,
				targetUserId + " " + before + " -> " + newRole);
		AppUser user = this.users.findById(targetUserId).orElseThrow();
		return new MemberView(targetUserId, user.getNickname(), newRole, target.getJoinedAt());
	}

	@Transactional
	public void removeMember(SessionUser actor, UUID groupId, UUID targetUserId) {
		Membership mine = require(actor, groupId, GroupRole.MEMBER);
		if (actor.id().equals(targetUserId)) {
			if (mine.getRole() == GroupRole.OWNER) {
				throw ApiException.badRequest("OWNER_CANNOT_LEAVE", "OWNER는 그룹을 떠날 수 없습니다.");
			}
			this.memberships.delete(mine);
			this.audit.record(actor.id(), "GROUP_LEFT", groupId, null);
			return;
		}
		Membership target = this.memberships.findById(new Membership.Key(groupId, targetUserId))
			.orElseThrow(ApiException::notFound);
		boolean allowed = (mine.getRole() == GroupRole.OWNER)
				|| (mine.getRole() == GroupRole.MANAGER && target.getRole() == GroupRole.MEMBER);
		if (!allowed) {
			throw ApiException.forbidden();
		}
		this.memberships.delete(target);
		this.audit.record(actor.id(), "GROUP_MEMBER_REMOVED", groupId, targetUserId.toString());
	}

	@Transactional
	public InviteCreated createInvite(SessionUser actor, UUID groupId, int expiresInDays, int maxUses) {
		require(actor, groupId, GroupRole.MANAGER);
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		Instant expiresAt = Instant.now().plus(Duration.ofDays(expiresInDays));
		GroupInvite invite = this.invites
			.save(new GroupInvite(groupId, hash(token), actor.id(), expiresAt, maxUses));
		this.audit.record(actor.id(), "INVITE_CREATED", groupId, invite.getId().toString());
		return new InviteCreated(InviteView.of(invite), token);
	}

	public List<InviteView> invites(SessionUser actor, UUID groupId) {
		require(actor, groupId, GroupRole.MANAGER);
		return this.invites.findByGroupIdOrderByCreatedAtDesc(groupId).stream().map(InviteView::of).toList();
	}

	@Transactional
	public void revokeInvite(SessionUser actor, UUID groupId, UUID inviteId) {
		require(actor, groupId, GroupRole.MANAGER);
		GroupInvite invite = this.invites.findById(inviteId)
			.filter((i) -> i.getGroupId().equals(groupId))
			.orElseThrow(ApiException::notFound);
		invite.revoke();
		this.audit.record(actor.id(), "INVITE_REVOKED", groupId, inviteId.toString());
	}

	public InvitePreview previewInvite(SessionUser actor, String token) {
		GroupInvite invite = this.invites.findByTokenHash(hash(token))
			.filter((i) -> i.isUsable(Instant.now()))
			.orElseThrow(GroupService::invalidInvite);
		StudyGroup group = this.groups.findById(invite.getGroupId()).orElseThrow(GroupService::invalidInvite);
		boolean alreadyMember = this.memberships.existsById(new Membership.Key(group.getId(), actor.id()));
		return new InvitePreview(group.getId(), group.getName(), group.getDescription(), alreadyMember);
	}

	@Transactional
	public UUID acceptInvite(SessionUser actor, String token) {
		GroupInvite invite = this.invites.findByTokenHashForUpdate(hash(token))
			.filter((i) -> i.isUsable(Instant.now()))
			.orElseThrow(GroupService::invalidInvite);
		Membership.Key key = new Membership.Key(invite.getGroupId(), actor.id());
		if (!this.memberships.existsById(key)) {
			this.memberships.save(new Membership(invite.getGroupId(), actor.id(), GroupRole.MEMBER));
			invite.markUsed();
			this.audit.record(actor.id(), "INVITE_ACCEPTED", invite.getGroupId(), invite.getId().toString());
		}
		return invite.getGroupId();
	}

	private Membership require(SessionUser actor, UUID groupId, GroupRole required) {
		Membership membership = this.memberships.findById(new Membership.Key(groupId, actor.id()))
			.orElseThrow(ApiException::notFound);
		if (!membership.getRole().atLeast(required)) {
			throw ApiException.forbidden();
		}
		return membership;
	}

	private static ApiException invalidInvite() {
		return new ApiException(HttpStatus.NOT_FOUND, "INVITE_INVALID", "유효하지 않거나 만료된 초대 링크입니다.");
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	public record GroupView(UUID id, String name, String description, GroupRole myRole, long memberCount,
			Instant createdAt) {

		static GroupView of(StudyGroup group, GroupRole role, long memberCount) {
			return new GroupView(group.getId(), group.getName(), group.getDescription(), role, memberCount,
					group.getCreatedAt());
		}

	}

	public record MemberView(UUID userId, String nickname, GroupRole role, Instant joinedAt) {
	}

	public record InviteView(UUID id, Instant expiresAt, int maxUses, int useCount, boolean revoked,
			Instant createdAt) {

		static InviteView of(GroupInvite invite) {
			return new InviteView(invite.getId(), invite.getExpiresAt(), invite.getMaxUses(), invite.getUseCount(),
					invite.isRevoked(), invite.getCreatedAt());
		}

	}

	/** token은 이 응답에서만 한 번 보여 준다 */
	public record InviteCreated(InviteView invite, String token) {
	}

	public record InvitePreview(UUID groupId, String groupName, String description, boolean alreadyMember) {
	}

}
