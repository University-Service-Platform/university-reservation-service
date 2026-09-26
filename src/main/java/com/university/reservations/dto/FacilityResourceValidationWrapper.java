package com.university.reservations.dto;

public record FacilityResourceValidationWrapper(
		Boolean success,
		String message,
		FacilityResourceValidationData data,
		String timestamp) {
}
