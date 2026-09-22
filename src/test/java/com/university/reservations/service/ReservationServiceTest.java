package com.university.reservations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.FacilityResourceInfo;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.exception.BusinessException;
import com.university.reservations.exception.ReservationConflictException;
import com.university.reservations.exception.ResourceNotFoundException;
import com.university.reservations.model.Reservation;
import com.university.reservations.model.ReservationApproval;
import com.university.reservations.model.ReservationApprovalAction;
import com.university.reservations.model.ReservationStatus;
import com.university.reservations.repository.ReservationApprovalRepository;
import com.university.reservations.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private ReservationApprovalRepository approvalRepository;

	@Mock
	private FacilityResourceClient facilityResourceClient;

	@Mock
	private AuthService authService;

	@InjectMocks
	private ReservationService reservationService;

	private LocalDateTime base = LocalDateTime.of(2026, 10, 20, 9, 0);

	private CreateReservationRequest validCreateRequest() {
		return new CreateReservationRequest(
				"resource-1", "student-7", base, base.plusHours(2), "Group study", 20);
	}

	private Reservation pendingReservation() {
		Reservation reservation = new Reservation();
		reservation.setId(100L);
		reservation.setResourceId("resource-1");
		reservation.setRequesterId("student-7");
		reservation.setStartTime(base);
		reservation.setEndTime(base.plusHours(2));
		reservation.setStatus(ReservationStatus.PENDING);
		reservation.setPurpose("Group study");
		reservation.setExpectedAttendees(20);
		return reservation;
	}

	@Test
	void createReservation_success() {
		FacilityResourceInfo resource = new FacilityResourceInfo("resource-1", "Hall A", 50, LocalTime.of(8, 0), LocalTime.of(22, 0));
		when(facilityResourceClient.getResource("resource-1")).thenReturn(resource);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.createReservation(validCreateRequest());

		assertEquals(ReservationStatus.PENDING, response.status());
		assertEquals("resource-1", response.resourceId());
		assertEquals(20, response.expectedAttendees());
	}

	@Test
	void createReservation_throwsWhenStartTimeAfterEndTime() {
		CreateReservationRequest request = new CreateReservationRequest(
				"resource-1", "student-7", base.plusHours(2), base, "Group study", 20);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(request));
		verify(facilityResourceClient, never()).getResource(any());
	}

	@Test
	void createReservation_throwsWhenCapacityExceeded() {
		FacilityResourceInfo resource = new FacilityResourceInfo("resource-1", "Room B", 10, null, null);
		when(facilityResourceClient.getResource("resource-1")).thenReturn(resource);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void createReservation_throwsWhenOutsideOperatingHours() {
		FacilityResourceInfo resource = new FacilityResourceInfo(
				"resource-1", "Room B", 100, LocalTime.of(8, 0), LocalTime.of(17, 0));
		when(facilityResourceClient.getResource("resource-1")).thenReturn(resource);

		CreateReservationRequest request = new CreateReservationRequest(
				"resource-1", "student-7", base.plusHours(18), base.plusHours(20), "Group study", 20);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(request));
	}

	@Test
	void approveReservation_success() {
		when(authService.getCurrentUserId()).thenReturn("manager-42");
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(0L);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.approveReservation(100L, new ApprovalRequest(null));

		assertEquals(ReservationStatus.APPROVED, response.status());
		ArgumentCaptor<ReservationApproval> captor = ArgumentCaptor.forClass(ReservationApproval.class);
		verify(approvalRepository).save(captor.capture());
		assertEquals(ReservationApprovalAction.APPROVED, captor.getValue().getAction());
		assertEquals("manager-42", captor.getValue().getActionBy());
	}

	@Test
	void approveReservation_throwsOnOverlapConflict() {
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(1L);

		assertThrows(ReservationConflictException.class,
				() -> reservationService.approveReservation(100L, new ApprovalRequest(null)));
		verify(approvalRepository, never()).save(any());
	}

	@Test
	void approveReservation_throwsOnInvalidStateTransition() {
		Reservation reservation = pendingReservation();
		reservation.setStatus(ReservationStatus.APPROVED);
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		assertThrows(BusinessException.class,
				() -> reservationService.approveReservation(100L, new ApprovalRequest(null)));
	}

	@Test
	void approveReservation_throwsWhenReservationNotFound() {
		when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

		assertThrows(ResourceNotFoundException.class,
				() -> reservationService.approveReservation(999L, new ApprovalRequest(null)));
	}

	@Test
	void rejectReservation_success() {
		when(authService.getCurrentUserId()).thenReturn("manager-42");
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.rejectReservation(100L, new ApprovalRequest("Room double-booked"));

		assertEquals(ReservationStatus.REJECTED, response.status());
		ArgumentCaptor<ReservationApproval> captor = ArgumentCaptor.forClass(ReservationApproval.class);
		verify(approvalRepository).save(captor.capture());
		assertEquals(ReservationApprovalAction.REJECTED, captor.getValue().getAction());
		assertEquals("Room double-booked", captor.getValue().getReason());
	}

	@Test
	void rejectReservation_throwsWhenReasonIsMissing() {
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		assertThrows(BusinessException.class,
				() -> reservationService.rejectReservation(100L, new ApprovalRequest(null)));
		verify(approvalRepository, never()).save(any());
	}

	@Test
	void rejectReservation_throwsOnInvalidStateTransition() {
		Reservation reservation = pendingReservation();
		reservation.setStatus(ReservationStatus.CANCELLED);
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		assertThrows(BusinessException.class,
				() -> reservationService.rejectReservation(100L, new ApprovalRequest("Too late")));
	}

	@Test
	void cancelReservation_success() {
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.cancelReservation(100L);

		assertEquals(ReservationStatus.CANCELLED, response.status());
	}

	@Test
	void cancelReservation_throwsWhenAlreadyCancelled() {
		Reservation reservation = pendingReservation();
		reservation.setStatus(ReservationStatus.CANCELLED);
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		assertThrows(BusinessException.class, () -> reservationService.cancelReservation(100L));
	}

	@Test
	void cancelReservation_throwsWhenRejected() {
		Reservation reservation = pendingReservation();
		reservation.setStatus(ReservationStatus.REJECTED);
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

		assertThrows(BusinessException.class, () -> reservationService.cancelReservation(100L));
	}

	@Test
	void listReservations_filtersByStatus() {
		Reservation reservation = pendingReservation();
		when(reservationRepository.findByStatus(ReservationStatus.PENDING)).thenReturn(java.util.List.of(reservation));

		var responses = reservationService.listReservations(null, null, ReservationStatus.PENDING);

		assertEquals(1, responses.size());
		assertTrue(responses.stream().allMatch(r -> r.status() == ReservationStatus.PENDING));
	}
}