package com.university.reservations.dto;

import com.university.reservations.model.ReservationStatus;
import java.time.LocalDateTime;

public record ReservationResponse(
		Long id,
		Long resourceId,
		String requesterId,
		LocalDateTime startTime,
		LocalDateTime endTime,
		ReservationStatus status,
		String purpose,
		Integer expectedAttendees,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}