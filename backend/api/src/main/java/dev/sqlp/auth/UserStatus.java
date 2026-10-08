package dev.sqlp.auth;

/**
 * 가입하면 PENDING이고, 시스템 관리자가 승인해야 ACTIVE가 되어 로그인할 수 있다.
 */
public enum UserStatus {

	PENDING, ACTIVE, REJECTED, SUSPENDED

}
