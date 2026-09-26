package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

public record FacilityResourceValidationData(
		Long resourceId,
		String resourceCode,
		Long facilityId,
		Boolean exists,
		Boolean active,
		Boolean available,
		Integer capacity,
		Boolean approvalRequired,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime operatingHoursStart,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime operatingHoursEnd,
		Boolean validForReservation,
		String message) {
}
