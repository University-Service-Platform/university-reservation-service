package com.university.reservations.dto;

public record ResourceAvailabilityWrapper(
		Boolean success,
		String message,
		ResourceAvailabilityData data,
		String timestamp) {
}
