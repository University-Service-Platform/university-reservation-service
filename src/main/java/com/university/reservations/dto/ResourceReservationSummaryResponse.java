package com.university.reservations.dto;

public record ResourceReservationSummaryResponse(
		String resourceId,
		long totalReservations,
		long approvedReservations,
		long cancelledReservations) {
}
