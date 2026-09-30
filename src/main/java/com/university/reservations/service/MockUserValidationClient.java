package com.university.reservations.service;

import com.university.reservations.dto.Group5EligibilityChecks;
import com.university.reservations.dto.Group5EligibilityData;
import com.university.reservations.dto.Group5UserValidationData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("mockUserValidationClient")
public class MockUserValidationClient implements UserValidationClient {

	private static final Logger log = LoggerFactory.getLogger(MockUserValidationClient.class);

	private final boolean enabled;

	public MockUserValidationClient(@Value("${group5.integration.enabled:false}") boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean isIntegrationEnabled() {
		return enabled;
	}

	@Override
	public Group5UserValidationData validateUser(String userId, String token) {
		log.info("Mocking Group 5 user validation for userId: {}", userId);
		return new Group5UserValidationData(
				userId,
				"STU001",
				"Mock User",
				"STUDENT",
				"ACTIVE",
				true,
				List.of("STUDENT", "RESOURCE_MANAGER"),
				null,
				null
		);
	}

	@Override
	public Group5UserValidationData validateUserWithRole(String userId, String requiredRole, String token) {
		log.info("Mocking Group 5 user validation with role {} for userId: {}", requiredRole, userId);
		boolean isAuthorized = "RESOURCE_MANAGER".equalsIgnoreCase(requiredRole) || "STUDENT".equalsIgnoreCase(requiredRole);
		return new Group5UserValidationData(
				userId,
				"STU001",
				"Mock User",
				"STUDENT",
				"ACTIVE",
				true,
				List.of("STUDENT", "RESOURCE_MANAGER"),
				isAuthorized,
				requiredRole
		);
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
		log.info("Mocking Group 5 eligibility validation for userId: {}", userId);
		Group5EligibilityChecks checks = new Group5EligibilityChecks(true, requiredRole, true, relationship, true);
		return new Group5EligibilityData(
				userId,
				"STU001",
				"ACTIVE",
				List.of("STUDENT", "RESOURCE_MANAGER"),
				true,
				List.of(),
				"User is eligible.",
				checks,
				List.of(),
				null
		);
	}
}
