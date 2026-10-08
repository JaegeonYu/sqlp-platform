package dev.sqlp.group;

import java.util.UUID;

import dev.sqlp.auth.SessionUser;
import dev.sqlp.common.ApiException;

import org.springframework.stereotype.Component;

/**
 * 그룹 단위 권한 확인. 멤버가 아니면 404(존재 여부 비노출), 역할이 모자라면 403.
 * 그룹에 속한 리소스(코스, 기수 등)는 모두 이 클래스로 확인한다.
 */
@Component
public class GroupAccess {

	private final MembershipRepository memberships;

	GroupAccess(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	public GroupRole require(SessionUser actor, UUID groupId, GroupRole required) {
		Membership membership = this.memberships.findById(new Membership.Key(groupId, actor.id()))
			.orElseThrow(ApiException::notFound);
		if (!membership.getRole().atLeast(required)) {
			throw ApiException.forbidden();
		}
		return membership.getRole();
	}

	public boolean isMember(UUID groupId, UUID userId) {
		return this.memberships.existsById(new Membership.Key(groupId, userId));
	}

}
