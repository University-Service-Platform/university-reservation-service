package com.university.reservations.dto;

import java.time.LocalDateTime;

public record ResourceAvailabilityCheckRequest(
		String resourceId,
		LocalDateTime startTime,
		LocalDateTime endTime) {
}
