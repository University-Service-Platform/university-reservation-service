package com.university.reservations.dto;

public record Group5ErrorEnvelope(
		Boolean success,
		Group5ErrorDetails error,
		String timestamp) {
}
