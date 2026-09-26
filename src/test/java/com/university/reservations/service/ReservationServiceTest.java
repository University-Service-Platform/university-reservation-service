package com.university.reservations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.Group5EligibilityChecks;
import com.university.reservations.dto.Group5EligibilityData;
import com.university.reservations.dto.Group5UserValidationData;
import com.university.reservations.dto.ReservationApprovalResponse;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.dto.ReservationStatusSummaryResponse;
import com.university.reservations.dto.ResourceAvailabilityData;
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
import org.springframework.security.access.AccessDeniedException;

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

	private Group5UserValidationData activeUserData(String userId, String role) {
		return new Group5UserValidationData(
				userId, "STU001", "Demo User", "STUDENT", "ACTIVE", true,
				List.of(role), true, role);
	}

	private Group5UserValidationData inactiveUserData(String userId) {
		return new Group5UserValidationData(
				userId, "STU001", "Demo User", "STUDENT", "INACTIVE", false,
				List.of("STUDENT"), false, null);
	}

	@Test
	void createReservation_activeValidUser_success() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(userValidationClient.validateUser("student-7")).thenReturn(activeUserData("student-7", "STUDENT"));
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
	void createReservation_requesterIdentityComesFromJwtSub() {
		when(authService.getCurrentUserId()).thenReturn("sub-user-999");
		when(userValidationClient.validateUser("sub-user-999")).thenReturn(activeUserData("sub-user-999", "STUDENT"));
		when(facilityResourceClient.validateResource(1L)).thenReturn(validResourceData(false));
		when(facilityResourceClient.checkAvailability(any(), any(), any(), any(), any())).thenReturn(validAvailabilityData(false));
		when(reservationRepository.countOverlappingApproved(any(), any(), any())).thenReturn(0L);
		when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

		// Body contains "student-7", but sub is "sub-user-999"
		ReservationResponse response = reservationService.createReservation(validCreateRequest());

		assertEquals("sub-user-999", response.requesterId());
	}

	@Test
	void createReservation_inactiveUser_throwsBusinessException() {
		when(authService.getCurrentUserId()).thenReturn("student-inactive");
		when(userValidationClient.validateUser("student-inactive")).thenReturn(inactiveUserData("student-inactive"));

		BusinessException ex = assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
		assertEquals("Requester is not active or valid in Identity Service", ex.getMessage());
		verify(facilityResourceClient, never()).validateResource(any());
	}

	@Test
	void createReservation_nonexistentUser_throwsResourceNotFoundException() {
		when(authService.getCurrentUserId()).thenReturn("user-404");
		when(userValidationClient.validateUser("user-404"))
				.thenThrow(new ResourceNotFoundException("User not found in Identity Service: usr-404"));

		assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(validCreateRequest()));
		verify(facilityResourceClient, never()).validateResource(any());
	}

	@Test
	void approveReservation_validResourceManager_success() {
		when(authService.getCurrentUserId()).thenReturn("manager-001");
		when(userValidationClient.validateUserWithRole("manager-001", "RESOURCE_MANAGER"))
				.thenReturn(new Group5UserValidationData("manager-001", "MGR001", "Manager", "STAFF", "ACTIVE", true, List.of("RESOURCE_MANAGER"), true, "RESOURCE_MANAGER"));

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
		assertEquals("manager-001", captor.getValue().getActionBy());
	}

	@Test
	void approveReservation_userMissingResourceManagerRole_throwsAccessDeniedException() {
		when(authService.getCurrentUserId()).thenReturn("student-001");
		when(userValidationClient.validateUserWithRole("student-001", "RESOURCE_MANAGER"))
				.thenReturn(new Group5UserValidationData("student-001", "STU001", "Student", "STUDENT", "ACTIVE", true, List.of("STUDENT"), false, "RESOURCE_MANAGER"));

		assertThrows(AccessDeniedException.class, () -> reservationService.approveReservation(100L, new ApprovalRequest("Looks good")));
		verify(reservationRepository, never()).findById(any());
	}

	@Test
	void validateUserEligibility_eligibleDepartmentUser_success() {
		Group5EligibilityChecks checks = new Group5EligibilityChecks(true, "STUDENT", true, "AFFILIATION", true);
		Group5EligibilityData eligibleData = new Group5EligibilityData(
				"student-1", "STU001", "ACTIVE", List.of("STUDENT"), true, List.of(), "User is eligible", checks, List.of(), null);

		when(userValidationClient.validateEligibility("student-1", "STUDENT", "AFFILIATION", "CS", null, null, null))
				.thenReturn(eligibleData);

		Group5EligibilityData result = reservationService.validateUserEligibility("student-1", "STUDENT", "AFFILIATION", "CS", null, null);
		assertNotNull(result);
		assertEquals(true, result.eligible());
	}

	@Test
	void validateUserEligibility_affiliationMismatch_throwsBusinessException() {
		Group5EligibilityChecks checks = new Group5EligibilityChecks(true, "STUDENT", true, "AFFILIATION", false);
		Group5EligibilityData ineligibleData = new Group5EligibilityData(
				"student-1", "STU001", "ACTIVE", List.of("STUDENT"), false, List.of("AFFILIATION_MISMATCH"), "User is not affiliated with department", checks, List.of(), null);

		when(userValidationClient.validateEligibility("student-1", "STUDENT", "AFFILIATION", "MATH", null, null, null))
				.thenReturn(ineligibleData);

		BusinessException ex = assertThrows(BusinessException.class, () ->
				reservationService.validateUserEligibility("student-1", "STUDENT", "AFFILIATION", "MATH", null, null));
		assertEquals("Eligibility validation failed: AFFILIATION_MISMATCH", ex.getMessage());
	}

	@Test
	void validateUserEligibility_validServiceResponsibility_success() {
		Group5EligibilityChecks checks = new Group5EligibilityChecks(true, "SERVICE_DESK_OFFICER", true, "RESPONSIBILITY", true);
		Group5EligibilityData eligibleData = new Group5EligibilityData(
				"sdo-1", "SDO001", "ACTIVE", List.of("SERVICE_DESK_OFFICER"), true, List.of(), "User is eligible", checks, List.of(), null);

		when(userValidationClient.validateEligibility("sdo-1", "SERVICE_DESK_OFFICER", "RESPONSIBILITY", null, null, "su-it-helpdesk", null))
				.thenReturn(eligibleData);

		Group5EligibilityData result = reservationService.validateUserEligibility("sdo-1", "SERVICE_DESK_OFFICER", "RESPONSIBILITY", null, null, "su-it-helpdesk");
		assertNotNull(result);
		assertEquals(true, result.eligible());
	}

	@Test
	void validateUserEligibility_noMatchingResponsibility_throwsBusinessException() {
		Group5EligibilityChecks checks = new Group5EligibilityChecks(true, "TECHNICIAN", true, "RESPONSIBILITY", false);
		Group5EligibilityData ineligibleData = new Group5EligibilityData(
				"tech-1", "TEC001", "ACTIVE", List.of("TECHNICIAN"), false, List.of("NO_MATCHING_RESPONSIBILITY"), "No matching responsibility", checks, List.of(), null);

		when(userValidationClient.validateEligibility("tech-1", "TECHNICIAN", "RESPONSIBILITY", null, null, "su-unknown", null))
				.thenReturn(ineligibleData);

		BusinessException ex = assertThrows(BusinessException.class, () ->
				reservationService.validateUserEligibility("tech-1", "TECHNICIAN", "RESPONSIBILITY", null, null, "su-unknown"));
		assertEquals("Eligibility validation failed: NO_MATCHING_RESPONSIBILITY", ex.getMessage());
	}

	@Test
	void createReservation_group5_401_throwsBusinessException() {
		when(authService.getCurrentUserId()).thenReturn("student-401");
		when(userValidationClient.validateUser("student-401"))
				.thenThrow(new BusinessException("Authentication failed with Identity Service: Authentication token is invalid or expired"));

		assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void createReservation_group5_404_throwsResourceNotFoundException() {
		when(authService.getCurrentUserId()).thenReturn("student-404");
		when(userValidationClient.validateUser("student-404"))
				.thenThrow(new ResourceNotFoundException("User not found in Identity Service: User with identifier 'student-404' was not found"));

		assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void createReservation_group5_502_throwsBusinessException() {
		when(authService.getCurrentUserId()).thenReturn("student-502");
		when(userValidationClient.validateUser("student-502"))
				.thenThrow(new BusinessException("Identity/Directory Service error: Directory Service returned an unexpected response"));

		assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
	}

	@Test
	void createReservation_group5_503_throwsBusinessException() {
		when(authService.getCurrentUserId()).thenReturn("student-503");
		when(userValidationClient.validateUser("student-503"))
				.thenThrow(new BusinessException("Identity/Directory Service is currently unavailable: The Directory Service is currently unavailable, so eligibility could not be determined. Please retry later."));

		BusinessException ex = assertThrows(BusinessException.class, () -> reservationService.createReservation(validCreateRequest()));
		assertEquals(true, ex.getMessage().contains("currently unavailable"));
	}

	@Test
	void createReservation_autoApprovedWhenNoApprovalRequired() {
		when(authService.getCurrentUserId()).thenReturn("student-7");
		when(userValidationClient.validateUser("student-7")).thenReturn(activeUserData("student-7", "STUDENT"));
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
		when(userValidationClient.validateUser("student-7")).thenReturn(activeUserData("student-7", "STUDENT"));
		FacilityResourceValidationData missing = new FacilityResourceValidationData(
				999L, null, null, false, false, false, null, false, null, null, false, "Resource with ID 999 does not exist");
		when(facilityResourceClient.validateResource(999L)).thenReturn(missing);

		CreateReservationRequest request = new CreateReservationRequest(
				999L, "student-7", futureBase, futureBase.plusHours(2), "Group study", 20);

		assertThrows(ResourceNotFoundException.class, () -> reservationService.createReservation(request));
	}

	@Test
	void rejectReservation_success() {
		when(authService.getCurrentUserId()).thenReturn("manager-42");
		when(userValidationClient.validateUserWithRole("manager-42", "RESOURCE_MANAGER"))
				.thenReturn(new Group5UserValidationData("manager-42", "MGR042", "Manager", "STAFF", "ACTIVE", true, List.of("RESOURCE_MANAGER"), true, "RESOURCE_MANAGER"));

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