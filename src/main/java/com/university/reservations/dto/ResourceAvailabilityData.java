package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceAvailabilityData(
		Long resourceId,
		String resourceCode,
		@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
		Boolean available,
		Boolean withinOperatingHours,
		Boolean capacitySufficient,
		Boolean userEligible,
		Boolean approvalRequired,
		String message) {
}
