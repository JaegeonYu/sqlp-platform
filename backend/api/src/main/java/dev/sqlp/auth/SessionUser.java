package dev.sqlp.auth;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 세션(Spring Session JDBC)에 저장되는 로그인 사용자. 로그인 방식(이메일/Google)과 관계없이 같은 형태다.
 */
public record SessionUser(UUID id, String email, String nickname, SystemRole systemRole)
		implements AuthenticatedPrincipal, Serializable {

	/** 세션 저장소의 principal_name 등에 쓰이는 이름. 바뀌지 않는 사용자 ID를 쓴다. */
	@Override
	@JsonIgnore
	public String getName() {
		return this.id.toString();
	}

	@JsonIgnore
	public boolean isAdmin() {
		return this.systemRole == SystemRole.ADMIN;
	}

	public Authentication toAuthentication() {
		List<SimpleGrantedAuthority> authorities = isAdmin()
				? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
				: List.of(new SimpleGrantedAuthority("ROLE_USER"));
		return UsernamePasswordAuthenticationToken.authenticated(this, null, authorities);
	}

}
