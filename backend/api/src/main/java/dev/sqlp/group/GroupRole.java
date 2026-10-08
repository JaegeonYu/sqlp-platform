package dev.sqlp.group;

/**
 * 그룹 안의 역할. 선언 순서가 권한 크기 순서다.
 */
public enum GroupRole {

	MEMBER, MANAGER, OWNER;

	public boolean atLeast(GroupRole required) {
		return compareTo(required) >= 0;
	}

}
