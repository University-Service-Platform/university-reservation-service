package com.university.reservations.service;

import com.university.reservations.dto.Group5EligibilityData;
import com.university.reservations.dto.Group5UserValidationData;

public interface UserValidationClient {

	Group5UserValidationData validateUser(String userId, String token);

	Group5UserValidationData validateUserWithRole(String userId, String requiredRole, String token);

	Group5EligibilityData validateEligibility(
			String userId,
			String requiredRole,
			String relationship,
			String departmentId,
			String facultyId,
			String serviceUnitId,
			String token);

	default Group5UserValidationData validateUser(String userId) {
		return validateUser(userId, null);
	}

	default Group5UserValidationData validateUserWithRole(String userId, String requiredRole) {
		return validateUserWithRole(userId, requiredRole, null);
	}

	boolean isIntegrationEnabled();
}
