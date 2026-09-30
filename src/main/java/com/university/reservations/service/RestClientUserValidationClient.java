package com.university.reservations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.reservations.dto.Group5EligibilityData;
import com.university.reservations.dto.Group5EligibilityResponse;
import com.university.reservations.dto.Group5ErrorEnvelope;
import com.university.reservations.dto.Group5UserValidationData;
import com.university.reservations.dto.Group5UserValidationResponse;
import com.university.reservations.exception.BusinessException;
import com.university.reservations.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Primary
public class RestClientUserValidationClient implements UserValidationClient {

	private static final Logger log = LoggerFactory.getLogger(RestClientUserValidationClient.class);

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final boolean enabled;

	public RestClientUserValidationClient(
			@Value("${identity-service.base-url:http://localhost:8001}") String baseUrl,
			@Value("${group5.integration.enabled:true}") boolean enabled,
			ObjectProvider<ObjectMapper> objectMapperProvider) {
		this.enabled = enabled;
		this.objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.build();
	}

	@Override
	public boolean isIntegrationEnabled() {
		return enabled;
	}

	@Override
	public Group5UserValidationData validateUser(String userId, String token) {
		return validateUserWithRole(userId, null, token);
	}

	@Override
	public Group5UserValidationData validateUserWithRole(String userId, String requiredRole, String token) {
		if (!enabled) {
			log.debug("Group 5 integration disabled. Skipping remote user validation for userId: {}", userId);
			return new Group5UserValidationData(
					userId,
					"STU001",
					"Dev User",
					"STUDENT",
					"ACTIVE",
					true,
					java.util.List.of("STUDENT", "RESOURCE_MANAGER"),
					requiredRole != null ? true : null,
					requiredRole
			);
		}

		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v1/validation/users/{user_id}");
		if (StringUtils.hasText(requiredRole)) {
			builder.queryParam("required_role", requiredRole);
		}

		String resolvedToken = resolveToken(token);
		String uri = builder.buildAndExpand(userId).toUriString();

		try {
			var requestSpec = restClient.get().uri(uri);
			if (StringUtils.hasText(resolvedToken)) {
				requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + resolvedToken);
			}

			Group5UserValidationResponse response = requestSpec.retrieve()
					.body(Group5UserValidationResponse.class);

			if (response == null || response.data() == null) {
				throw new BusinessException("Empty response from Identity Service user validation");
			}

			return response.data();
		} catch (RestClientResponseException ex) {
			throw handleHttpError(ex);
		} catch (ResourceAccessException ex) {
			log.error("Group 5 Identity Service is unreachable: {}", ex.getMessage());
			throw new BusinessException("Identity Service is currently unavailable (connection failed). Please retry later.");
		}
	}

	@Override
	public Group5EligibilityData validateEligibility(
			String userId,
			String requiredRole,
			String relationship,
			String departmentId,
			String facultyId,
			String serviceUnitId,
			String token) {
		if (!enabled) {
			log.debug("Group 5 integration disabled. Skipping eligibility check for userId: {}", userId);
			return new Group5EligibilityData(
					userId,
					"STU001",
					"ACTIVE",
					java.util.List.of("STUDENT"),
					true,
					java.util.List.of(),
					"Integration disabled - default eligible",
					null,
					java.util.List.of(),
					null
			);
		}

		UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v1/validation/users/{user_id}/eligibility");
		if (StringUtils.hasText(requiredRole)) {
			builder.queryParam("required_role", requiredRole);
		}
		if (StringUtils.hasText(relationship)) {
			builder.queryParam("relationship", relationship);
		}
		if (StringUtils.hasText(departmentId)) {
			builder.queryParam("department_id", departmentId);
		}
		if (StringUtils.hasText(facultyId)) {
			builder.queryParam("faculty_id", facultyId);
		}
		if (StringUtils.hasText(serviceUnitId)) {
			builder.queryParam("service_unit_id", serviceUnitId);
		}

		String resolvedToken = resolveToken(token);
		String uri = builder.buildAndExpand(userId).toUriString();

		try {
			var requestSpec = restClient.get().uri(uri);
			if (StringUtils.hasText(resolvedToken)) {
				requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + resolvedToken);
			}

			Group5EligibilityResponse response = requestSpec.retrieve()
					.body(Group5EligibilityResponse.class);

			if (response == null || response.data() == null) {
				throw new BusinessException("Empty response from Identity Service eligibility check");
			}

			return response.data();
		} catch (RestClientResponseException ex) {
			throw handleHttpError(ex);
		} catch (ResourceAccessException ex) {
			log.error("Group 5 Identity Service is unreachable during eligibility check: {}", ex.getMessage());
			throw new BusinessException("Identity Service is currently unavailable (connection failed). Please retry later.");
		}
	}

	private String resolveToken(String explicitToken) {
		if (StringUtils.hasText(explicitToken)) {
			return explicitToken;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null) {
			if (auth.getPrincipal() instanceof Jwt jwt) {
				return jwt.getTokenValue();
			} else if (auth.getCredentials() instanceof Jwt jwt) {
				return jwt.getTokenValue();
			} else if (auth.getCredentials() instanceof String strToken && StringUtils.hasText(strToken)) {
				return strToken;
			}
		}
		return null;
	}

	private RuntimeException handleHttpError(RestClientResponseException ex) {
		String responseBody = ex.getResponseBodyAsString();
		String errorCode = null;
		String errorMessage = null;

		if (StringUtils.hasText(responseBody)) {
			try {
				Group5ErrorEnvelope env = objectMapper.readValue(responseBody, Group5ErrorEnvelope.class);
				if (env != null && env.error() != null) {
					errorCode = env.error().code();
					errorMessage = env.error().message();
				}
			} catch (Exception e) {
				log.warn("Could not parse Group 5 error envelope: {}", responseBody);
			}
		}

		if (errorCode == null) {
			errorCode = switch (ex.getStatusCode().value()) {
				case 400 -> "BAD_REQUEST";
				case 401 -> "UNAUTHORIZED";
				case 403 -> "ACCOUNT_INACTIVE";
				case 404 -> "USER_NOT_FOUND";
				case 422 -> "VALIDATION_ERROR";
				case 502 -> "DEPENDENCY_ERROR";
				case 503 -> "DEPENDENCY_UNAVAILABLE";
				default -> "INTERNAL_SERVER_ERROR";
			};
		}
		if (errorMessage == null) {
			errorMessage = ex.getMessage();
		}

		log.error("Group 5 API error: HTTP {}, code: {}, message: {}", ex.getStatusCode().value(), errorCode, errorMessage);

		return switch (errorCode) {
			case "DEPENDENCY_UNAVAILABLE" -> new BusinessException("Identity/Directory Service is currently unavailable: " + errorMessage);
			case "DEPENDENCY_ERROR" -> new BusinessException("Identity/Directory Service error: " + errorMessage);
			case "USER_NOT_FOUND" -> new ResourceNotFoundException("User not found in Identity Service: " + errorMessage);
			case "ACCOUNT_INACTIVE" -> new BusinessException("User account is inactive: " + errorMessage);
			case "UNAUTHORIZED", "INVALID_TOKEN", "INVALID_CREDENTIALS" -> new BusinessException("Authentication failed with Identity Service: " + errorMessage);
			case "INVALID_IDENTIFIER_FORMAT", "BAD_REQUEST", "VALIDATION_ERROR" -> new BusinessException("Invalid request to Identity Service: " + errorMessage);
			default -> new BusinessException("Identity Service error [" + errorCode + "]: " + errorMessage);
		};
	}
}
