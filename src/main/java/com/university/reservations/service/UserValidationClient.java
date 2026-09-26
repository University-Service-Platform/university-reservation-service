package com.university.reservations.service;

import com.university.reservations.dto.UserValidationData;

public interface UserValidationClient {

	UserValidationData validateUser(String userId);

	boolean isIntegrationEnabled();
}
