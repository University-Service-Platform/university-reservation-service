package com.university.reservations.dto;

import java.util.List;

public record UserValidationData(
		String userId,
		boolean active,
		List<String> roles,
		String department,
		String serviceUnit,
		String message) {
}
