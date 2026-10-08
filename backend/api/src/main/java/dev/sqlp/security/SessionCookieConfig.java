package dev.sqlp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Session 쿠키 속성. server.servlet.session.cookie.* 는 Spring Session 쿠키에 적용되지 않으므로 직접 지정한다.
 */
@Configuration(proxyBeanMethods = false)
class SessionCookieConfig {

	static final String COOKIE_NAME = "SQLP_SESSION";

	@Bean
	CookieSerializer cookieSerializer(@Value("${sqlp.session.cookie-secure:true}") boolean secure) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(secure);
		return serializer;
	}

}
