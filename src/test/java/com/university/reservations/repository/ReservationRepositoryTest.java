package com.university.reservations.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.university.reservations.model.Reservation;
import com.university.reservations.model.ReservationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ReservationRepositoryTest {

	@Autowired
	private ReservationRepository reservationRepository;

	private final LocalDateTime base = LocalDateTime.of(2026, 10, 20, 9, 0);

	@Test
	void countOverlappingApproved_detectsOverlap() {
		saveReservation("resource-1", base, base.plusHours(2), ReservationStatus.APPROVED);

		long count = reservationRepository.countOverlappingApproved(
				"resource-1", base.plusHours(1), base.plusHours(3));

		assertEquals(1, count);
	}

	@Test
	void countOverlappingApproved_ignoresPendingReservations() {
		saveReservation("resource-1", base, base.plusHours(2), ReservationStatus.PENDING);

		long count = reservationRepository.countOverlappingApproved(
				"resource-1", base.plusHours(1), base.plusHours(3));

		assertEquals(0, count);
	}

	@Test
	void countOverlappingApproved_ignoresOtherResources() {
		saveReservation("resource-2", base, base.plusHours(2), ReservationStatus.APPROVED);

		long count = reservationRepository.countOverlappingApproved(
				"resource-1", base.plusHours(1), base.plusHours(3));

		assertEquals(0, count);
	}

	@Test
	void countOverlappingApproved_adjacentTimeSlotsDoNotOverlap() {
		saveReservation("resource-1", base, base.plusHours(2), ReservationStatus.APPROVED);

		long count = reservationRepository.countOverlappingApproved(
				"resource-1", base.plusHours(2), base.plusHours(4));

		assertEquals(0, count);
	}

	private void saveReservation(String resourceId, LocalDateTime start, LocalDateTime end, ReservationStatus status) {
		Reservation reservation = new Reservation();
		reservation.setResourceId(resourceId);
		reservation.setRequesterId("student-7");
		reservation.setStartTime(start);
		reservation.setEndTime(end);
		reservation.setStatus(status);
		reservation.setPurpose("Group study");
		reservation.setExpectedAttendees(20);
		reservationRepository.save(reservation);
	}
}