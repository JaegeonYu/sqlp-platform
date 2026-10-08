package dev.sqlp.security;

import dev.sqlp.auth.AppUserRepository;
import dev.sqlp.auth.AuthProperties;
import dev.sqlp.auth.GoogleLoginHandler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** GOOGLE_CLIENT_ID와 GOOGLE_CLIENT_SECRET이 모두 있을 때만 Google 로그인을 켠다 */
	@Bean
	@ConditionalOnExpression("!'${sqlp.auth.google-client-id:}'.isBlank() and !'${sqlp.auth.google-client-secret:}'.isBlank()")
	ClientRegistrationRepository clientRegistrationRepository(AuthProperties properties) {
		return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
			.clientId(properties.googleClientId())
			.clientSecret(properties.googleClientSecret())
			.scope("openid", "email", "profile")
			.build());
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository,
			AppUserRepository users, GoogleLoginHandler googleLoginHandler,
			ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
		http.csrf((csrf) -> csrf.spa())
			.securityContext((context) -> context.securityContextRepository(securityContextRepository))
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/api/system/**", "/actuator/health", "/error")
				.permitAll()
				.requestMatchers("/api/auth/csrf", "/api/auth/providers", "/api/auth/signup", "/api/auth/login")
				.permitAll()
				.requestMatchers("/oauth2/**", "/login/oauth2/**")
				.permitAll()
				.requestMatchers("/api/admin/**")
				.hasRole("ADMIN")
				.requestMatchers("/api/**")
				.authenticated()
				.anyRequest()
				.denyAll())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.logout((logout) -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			.addFilterBefore(new ActiveUserFilter(users, securityContextRepository), AuthorizationFilter.class);
		if (clientRegistrations.getIfAvailable() != null) {
			http.oauth2Login((oauth2) -> oauth2.successHandler(googleLoginHandler).failureHandler(googleLoginHandler));
		}
		return http.build();
	}

}
