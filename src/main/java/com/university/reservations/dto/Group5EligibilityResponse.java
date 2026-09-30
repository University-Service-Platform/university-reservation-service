package com.university.reservations.dto;

public record Group5EligibilityResponse(
		Boolean success,
		Group5EligibilityData data,
		Group5ErrorDetails error,
		String timestamp) {
}
