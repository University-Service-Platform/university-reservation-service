package com.university.reservations.dto;

import com.university.reservations.model.ReservationApprovalAction;
import java.time.LocalDateTime;

public record ReservationApprovalResponse(
		Long id,
		Long reservationId,
		String actionBy,
		ReservationApprovalAction action,
		String reason,
		LocalDateTime actionTimestamp) {
}
