package com.university.reservations.config;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Value("${reservation-service.jwt.roles-claim:roles}")
	private String rolesClaim;

	@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:${identity-service.base-url:http://localhost:8001}/.well-known/jwks.json}")
	private String jwkSetUri;

	@Value("${reservation-service.jwt.required-issuer:university-identity-service}")
	private String requiredIssuer;

	@Value("${reservation-service.jwt.required-audience:university-services-platform}")
	private String requiredAudience;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	@ConditionalOnMissingBean
	public JwtDecoder jwtDecoder(Environment environment) {
		// Strict production Group 5 JWKS decoder
		if (StringUtils.hasText(jwkSetUri) && !environment.matchesProfiles("dev")) {
			NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

			OAuth2TokenValidator<Jwt> withIssuer = new JwtIssuerValidator(requiredIssuer);
			OAuth2TokenValidator<Jwt> withTimestamp = new JwtTimestampValidator();
			OAuth2TokenValidator<Jwt> withAudienceAndSub = jwt -> {
				List<String> audience = jwt.getAudience();
				if (audience == null || !audience.contains(requiredAudience)) {
					OAuth2Error error = new OAuth2Error("invalid_token", "Invalid audience: " + audience + ", expected: " + requiredAudience, null);
					return OAuth2TokenValidatorResult.failure(error);
				}
				String sub = jwt.getSubject();
				if (!StringUtils.hasText(sub)) {
					OAuth2Error error = new OAuth2Error("invalid_token", "Missing sub claim in JWT", null);
					return OAuth2TokenValidatorResult.failure(error);
				}
				return OAuth2TokenValidatorResult.success();
			};

			OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withTimestamp, withIssuer, withAudienceAndSub);
			jwtDecoder.setJwtValidator(validator);
			return jwtDecoder;
		}

		// Dev profile / testing fallback JwtDecoder
		return createDevJwtDecoder();
	}

	private JwtDecoder createDevJwtDecoder() {
		return token -> {
			Instant now = Instant.now();
			String sub = (StringUtils.hasText(token) && !token.equalsIgnoreCase("bearer")) ? token : "dev-user";
			Map<String, Object> headers = Map.of("alg", "none");
			Map<String, Object> claims = Map.of(
					"sub", sub,
					rolesClaim, List.of("STUDENT", "RESOURCE_MANAGER"),
					"iss", requiredIssuer,
					"aud", List.of(requiredAudience),
					"iat", now,
					"exp", now.plusSeconds(3600));
			return new Jwt(token, now, now.plusSeconds(3600), headers, claims);
		};
	}

	private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> {
			Collection<String> roles = jwt.getClaimAsStringList(rolesClaim);
			if (roles == null) {
				return List.of();
			}
			return roles.stream()
					.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
					.map(SimpleGrantedAuthority::new)
					.collect(Collectors.toList());
		});
		return converter;
	}
}