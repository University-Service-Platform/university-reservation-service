package com.university.reservations.dto;

public record Group5UserValidationResponse(
		Boolean success,
		Group5UserValidationData data,
		Group5ErrorDetails error,
		String timestamp) {
}
