package com.university.reservations.dto;

public record ResourceReservationSummaryResponse(
		Long resourceId,
		long totalReservations,
		long approvedReservations,
		long cancelledReservations) {
}
