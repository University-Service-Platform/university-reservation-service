package com.university.reservations.dto;

public record ReservationStatusSummaryResponse(
		long pending,
		long approved,
		long rejected,
		long cancelled) {
}
