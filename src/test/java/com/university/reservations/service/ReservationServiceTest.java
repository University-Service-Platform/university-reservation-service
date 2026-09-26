package com.university.reservations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.ReservationApprovalResponse;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.dto.ReservationStatusSummaryResponse;
import com.university.reservations.dto.ResourceAvailabilityData;
import com.university.reservations.dto.UserValidationData;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
	private UserValidationClient userValidationClient;

	@Mock
	private AuthService authService;

	@InjectMocks
	private ReservationService reservationService;

	private LocalDateTime futureBase;

	@BeforeEach
	void setUp() {
		futureBase = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
	}

	private CreateReservationRequest validCreateRequest() {
		return new CreateReservationRequest(
				1L, "student-7", futureBase, futureBase.plusHours(2), "Group study", 20);
	}

	private FacilityResourceValidationData validResourceData(boolean approvalRequired) {
		return new FacilityResourceValidationData(
				1L, "LAB-101", 10L, true, true, true, 50, approvalRequired,
				LocalTime.of(8, 0), LocalTime.of(22, 0), true, "Resource is valid");
	}

	private ResourceAvailabilityData validAvailabilityData(boolean approvalRequired) {
		return new ResourceAvailabilityData(
				1L, "LAB-101", futureBase.toLocalDate(), futureBase.toLocalTime(), futureBase.plusHours(2).toLocalTime(),
				true, true, true, true, approvalRequired, "Resource is available");
	}

	private Reservation pendingReservation() {
		Reservation reservation = new Reservation();
		reservation.setId(100L);
		reservation.setResourceId(1L);
		reservation.setRequesterId("student-7");
		reservation.setStartTime(futureBase);
		reservation.setEndTime(futureBase.plusHours(2));
		reservation.setStatus(ReservationStatus.PENDING);
		reservation.setPurpose("Group study");
		reservation.setExpectedAttendees(20);
		return reservation;
	}

	@Test
	void createReservation_pendingWhenApprovalRequired() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(true));
		when(facilityResourceClient.checkAvailability(any(), any(), any(), any(), any())).thenReturn(validAvailabilityData(true));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(0L);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.createReservation(validCreateRequest());

		assertEquals(ReservationStatus.PENDING, response.status());
		assertEquals(1L, response.resourceId());
		assertEquals("student-7", response.requesterId());
	}

	@Test
	void createReservation_autoApprovedWhenNoApprovalRequired() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(false));
		when(facilityResourceClient.checkAvailability(any(), any(), any(), any(), any())).thenReturn(validAvailabilityData(false));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(0L);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.createReservation(validCreateRequest());

		assertEquals(ReservationStatus.APPROVED, response.status());
		verify(approvalRepository).save(any(ReservationApproval.class));
	}

	@Test
	void createReservation_throwsWhenResourceDoesNotExist() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		FacilityResourceValidationData missing = new FacilityResourceValidationData(
				999L, null, null, false, false, false, null, false, null, null, false, "Resource with ID 999 does not exist");
		when(facilityResourceClient.validateResource(999L)).thenReturn(missing);

		CreateReservationRequest request = new CreateReservationRequest(
				999L, "student-7", futureBase, futureBase.plusHours(2), "Group study", 20);

		assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(request));
	}

	@Test
	void createReservation_throwsWhenStartTimeAfterEndTime() {
		CreateReservationRequest request = new CreateReservationRequest(
				1L, "student-7", futureBase.plusHours(2), futureBase, "Group study", 20);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(request));
		verify(facilityResourceClient, never()).validateResource(any());
	}

	@Test
	void createReservation_throwsWhenStartTimeInPast() {
		CreateReservationRequest request = new CreateReservationRequest(
				1L, "student-7", LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1), "Group study", 20);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(request));
		verify(facilityResourceClient, never()).validateResource(any());
	}

	@Test
	void createReservation_throwsWhenOutsideOperatingHours() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(true));

		ResourceAvailabilityData outsideHours = new ResourceAvailabilityData(
				1L, "LAB-101", futureBase.toLocalDate(), futureBase.toLocalTime(), futureBase.plusHours(2).toLocalTime(),
				false, false, true, true, true, "Outside operating hours");
		when(facilityResourceClient.checkAvailability(any(), any(), any(), any(), any())).thenReturn(outsideHours);

		assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void createReservation_throwsOnConflict() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(true));
		when(facilityResourceClient.checkAvailability(any(), any(), any(), any(), any())).thenReturn(validAvailabilityData(true));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(1L);

		assertThrows(ReservationConflictException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void approveReservation_success() {
		when(authService.getCurrentUserId()).thenReturn("manager-42");
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(0L);
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(true));
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.approveReservation(100L, new ApprovalRequest("Looks good"));

		assertEquals(ReservationStatus.APPROVED, response.status());
		ArgumentCaptor<ReservationApproval> captor = ArgumentCaptor.forClass(ReservationApproval.class);
		verify(approvalRepository).save(captor.capture());
		assertEquals(ReservationApprovalAction.APPROVED, captor.getValue().getAction());
		assertEquals("manager-42", captor.getValue().getActionBy());
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
	void cancelReservation_success() {
		Reservation reservation = pendingReservation();
		when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReservationResponse response = reservationService.cancelReservation(100L);

		assertEquals(ReservationStatus.CANCELLED, response.status());
	}

	@Test
	void getStatusSummary_returnsCounts() {
		when(reservationRepository.countByStatus(ReservationStatus.PENDING)).thenReturn(5L);
		when(reservationRepository.countByStatus(ReservationStatus.APPROVED)).thenReturn(10L);
		when(reservationRepository.countByStatus(ReservationStatus.REJECTED)).thenReturn(2L);
		when(reservationRepository.countByStatus(ReservationStatus.CANCELLED)).thenReturn(3L);

		ReservationStatusSummaryResponse summary = reservationService.getStatusSummary();

		assertEquals(5L, summary.pending());
		assertEquals(10L, summary.approved());
		assertEquals(2L, summary.rejected());
		assertEquals(3L, summary.cancelled());
	}
}