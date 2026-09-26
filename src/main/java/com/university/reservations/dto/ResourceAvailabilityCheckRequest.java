package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

public record ResourceAvailabilityCheckRequest(
		Long resourceId,
		@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm:ss") LocalTime endTime,
		Integer requestedCapacity,
		String userRole) {
}
