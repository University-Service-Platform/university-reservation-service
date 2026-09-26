package com.university.reservations.service;

import com.university.reservations.dto.UserValidationData;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
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
	public UserValidationData validateUser(String userId) {
		if (!enabled) {
			log.debug("Group 5 integration disabled. Skipping remote user validation for userId: {}", userId);
			return new UserValidationData(userId, true, List.of("STUDENT"), "Computer Science", "Main Campus", "Group 5 integration disabled (Mock Mode)");
		}
		log.info("Mocking Group 5 user validation for userId: {}", userId);
		return new UserValidationData(userId, true, List.of("STUDENT", "FACULTY"), "Engineering", "Lab 1", "Mock validation successful");
	}
}
