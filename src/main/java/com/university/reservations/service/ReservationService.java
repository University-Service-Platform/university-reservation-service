package com.university.reservations.service;

import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.ReservationApprovalResponse;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.dto.ReservationStatusSummaryResponse;
import com.university.reservations.dto.ResourceAvailabilityData;
import com.university.reservations.dto.ResourceReservationSummaryResponse;
import com.university.reservations.dto.UsageTrendResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final ReservationApprovalRepository approvalRepository;
	private final FacilityResourceClient facilityResourceClient;
	private final UserValidationClient userValidationClient;
	private final AuthService authService;

	public ReservationService(ReservationRepository reservationRepository,
			ReservationApprovalRepository approvalRepository,
			FacilityResourceClient facilityResourceClient,
			UserValidationClient userValidationClient,
			AuthService authService) {
		this.reservationRepository = reservationRepository;
		this.approvalRepository = approvalRepository;
		this.facilityResourceClient = facilityResourceClient;
		this.userValidationClient = userValidationClient;
		this.authService = authService;
	}

	@Transactional
	public ReservationResponse createReservation(CreateReservationRequest request) {
		request.validate();

		String requesterId = resolveRequesterId(request.requesterId());

		// Optional Group 5 User Eligibility check
		if (userValidationClient.isIntegrationEnabled()) {
			UserValidationData userData = userValidationClient.validateUser(requesterId);
			if (!userData.active()) {
				throw new BusinessException("Requester is not active or eligible to make reservations");
			}
		}

		// STEP 1: Resource Validation
		FacilityResourceValidationData validationData = facilityResourceClient.validateResource(request.resourceId());
		if (Boolean.FALSE.equals(validationData.exists())) {
			throw new ResourceNotFoundException("Resource with ID " + request.resourceId() + " does not exist");
		}
		if (Boolean.FALSE.equals(validationData.validForReservation())) {
			String msg = validationData.message() != null ? validationData.message() : "Resource is not valid for reservation";
			throw new BusinessException("Resource invalid for reservation: " + msg);
		}

		// STEP 2: Resource Availability Check
		String userRole = "STUDENT"; // Default or derived from security context if available
		ResourceAvailabilityData availabilityData = facilityResourceClient.checkAvailability(
				request.resourceId(),
				request.startTime(),
				request.endTime(),
				request.expectedAttendees(),
				userRole);

		if (Boolean.FALSE.equals(availabilityData.available())) {
			if (Boolean.FALSE.equals(availabilityData.withinOperatingHours())) {
				throw new BusinessException("Requested time is outside facility operating hours");
			}
			if (Boolean.FALSE.equals(availabilityData.capacitySufficient())) {
				throw new BusinessException("Expected attendees exceed resource capacity");
			}
			if (Boolean.FALSE.equals(availabilityData.userEligible())) {
				throw new BusinessException("User is not eligible to reserve this resource");
			}
			String msg = availabilityData.message() != null ? availabilityData.message() : "Resource is unavailable for reservation";
			throw new BusinessException(msg);
		}

		// STEP 3: Check database for overlapping APPROVED reservations
		long overlaps = reservationRepository.countOverlappingApproved(
				request.resourceId(), request.startTime(), request.endTime());
		if (overlaps > 0) {
			throw new ReservationConflictException("Resource is already reserved for the requested time period.");
		}

		// STEP 4: Determine initial status based on approvalRequired
		boolean approvalRequired = Boolean.TRUE.equals(availabilityData.approvalRequired())
				|| Boolean.TRUE.equals(validationData.approvalRequired());

		Reservation reservation = new Reservation();
		reservation.setResourceId(request.resourceId());
		reservation.setRequesterId(requesterId);
		reservation.setStartTime(request.startTime());
		reservation.setEndTime(request.endTime());
		reservation.setPurpose(request.purpose());
		reservation.setExpectedAttendees(request.expectedAttendees());

		if (approvalRequired) {
			reservation.setStatus(ReservationStatus.PENDING);
			reservation = reservationRepository.save(reservation);
		} else {
			reservation.setStatus(ReservationStatus.APPROVED);
			reservation = reservationRepository.save(reservation);
			recordApproval(reservation, "SYSTEM_AUTO_APPROVE", ReservationApprovalAction.APPROVED,
					"Auto-approved: Resource does not require manager approval");
		}

		return toResponse(reservation);
	}

	@Transactional(readOnly = true)
	public ReservationResponse getReservation(Long id) {
		return toResponse(findReservation(id));
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listReservations(String requesterId, Long resourceId, ReservationStatus status) {
		List<Reservation> result;
		if (status != null) {
			result = new ArrayList<>(reservationRepository.findByStatus(status));
		} else if (resourceId != null) {
			result = new ArrayList<>(reservationRepository.findByResourceId(resourceId));
		} else if (requesterId != null) {
			result = new ArrayList<>(reservationRepository.findByRequesterId(requesterId));
		} else {
			result = new ArrayList<>(reservationRepository.findAll());
		}

		if (requesterId != null) {
			result.removeIf(reservation -> !requesterId.equals(reservation.getRequesterId()));
		}
		if (resourceId != null) {
			result.removeIf(reservation -> !resourceId.equals(reservation.getResourceId()));
		}
		if (status != null) {
			result.removeIf(reservation -> status != reservation.getStatus());
		}

		return result.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public List<ReservationResponse> getPendingReservations() {
		return reservationRepository.findByStatus(ReservationStatus.PENDING)
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> getMyReservations() {
		String currentUserId = authService.getCurrentUserId();
		return reservationRepository.findByRequesterId(currentUserId)
				.stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<ReservationApprovalResponse> getReservationHistory(Long reservationId) {
		findReservation(reservationId);
		return approvalRepository.findByReservationId(reservationId)
				.stream()
				.map(app -> new ReservationApprovalResponse(
						app.getId(),
						app.getReservation().getId(),
						app.getActionBy(),
						app.getAction(),
						app.getReason(),
						app.getActionTimestamp()))
				.toList();
	}

	@Transactional
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public ReservationResponse approveReservation(Long id, ApprovalRequest request) {
		Reservation reservation = findReservation(id);
		ensurePending(reservation);

		long overlaps = reservationRepository.countOverlappingApproved(
				reservation.getResourceId(), reservation.getStartTime(), reservation.getEndTime());
		if (overlaps > 0) {
			throw new ReservationConflictException("Resource is already reserved for the requested time period.");
		}

		facilityResourceClient.validateResource(reservation.getResourceId());

		reservation.setStatus(ReservationStatus.APPROVED);
		reservationRepository.save(reservation);
		recordApproval(reservation, authService.getCurrentUserId(), ReservationApprovalAction.APPROVED,
				request == null ? null : request.reason());

		return toResponse(reservation);
	}

	@Transactional
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public ReservationResponse rejectReservation(Long id, ApprovalRequest request) {
		Reservation reservation = findReservation(id);
		ensurePending(reservation);

		if (request == null || !StringUtils.hasText(request.reason())) {
			throw new BusinessException("A written reason is mandatory when rejecting a reservation");
		}

		reservation.setStatus(ReservationStatus.REJECTED);
		reservationRepository.save(reservation);
		recordApproval(reservation, authService.getCurrentUserId(), ReservationApprovalAction.REJECTED, request.reason());

		return toResponse(reservation);
	}

	@Transactional
	public ReservationResponse cancelReservation(Long id) {
		Reservation reservation = findReservation(id);
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {
			throw new BusinessException("Reservation is already cancelled");
		}
		if (reservation.getStatus() == ReservationStatus.REJECTED) {
			throw new BusinessException("Cannot cancel a rejected reservation");
		}
		if (reservation.getStatus() != ReservationStatus.PENDING
				&& reservation.getStatus() != ReservationStatus.APPROVED) {
			throw new BusinessException("Only PENDING or APPROVED reservations can be cancelled");
		}

		reservation.setStatus(ReservationStatus.CANCELLED);
		return toResponse(reservationRepository.save(reservation));
	}

	@Transactional(readOnly = true)
	public ReservationStatusSummaryResponse getStatusSummary() {
		long pending = reservationRepository.countByStatus(ReservationStatus.PENDING);
		long approved = reservationRepository.countByStatus(ReservationStatus.APPROVED);
		long rejected = reservationRepository.countByStatus(ReservationStatus.REJECTED);
		long cancelled = reservationRepository.countByStatus(ReservationStatus.CANCELLED);
		return new ReservationStatusSummaryResponse(pending, approved, rejected, cancelled);
	}

	@Transactional(readOnly = true)
	public List<ResourceReservationSummaryResponse> getResourceSummaries() {
		List<Long> resourceIds = reservationRepository.findDistinctResourceIds();
		List<ResourceReservationSummaryResponse> summaries = new ArrayList<>();
		for (Long resId : resourceIds) {
			long total = reservationRepository.countByResourceId(resId);
			long approved = reservationRepository.countByResourceIdAndStatus(resId, ReservationStatus.APPROVED);
			long cancelled = reservationRepository.countByResourceIdAndStatus(resId, ReservationStatus.CANCELLED);
			summaries.add(new ResourceReservationSummaryResponse(resId, total, approved, cancelled));
		}
		return summaries;
	}

	@Transactional(readOnly = true)
	public List<UsageTrendResponse> getResourceTrend(Long resourceId) {
		List<Reservation> reservations = reservationRepository.findByResourceId(resourceId);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		Map<String, Long> trendMap = reservations.stream()
				.collect(Collectors.groupingBy(r -> r.getStartTime().format(formatter), Collectors.counting()));

		return trendMap.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new UsageTrendResponse(entry.getKey(), entry.getValue()))
				.toList();
	}

	private String resolveRequesterId(String requestedRequesterId) {
		String currentUserId;
		try {
			currentUserId = authService.getCurrentUserId();
		} catch (Exception ex) {
			currentUserId = null;
		}

		if (StringUtils.hasText(currentUserId)) {
			return currentUserId;
		}
		if (StringUtils.hasText(requestedRequesterId)) {
			return requestedRequesterId;
		}
		throw new BusinessException("Requester identity could not be determined from authentication context");
	}

	private void recordApproval(Reservation reservation, String actionBy, ReservationApprovalAction action, String reason) {
		ReservationApproval approval = new ReservationApproval();
		approval.setReservation(reservation);
		approval.setActionBy(actionBy);
		approval.setAction(action);
		approval.setReason(reason);
		approvalRepository.save(approval);
	}

	private Reservation findReservation(Long id) {
		return reservationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
	}

	private void ensurePending(Reservation reservation) {
		if (reservation.getStatus() != ReservationStatus.PENDING) {
			throw new BusinessException("Only PENDING reservations can be approved or rejected, current status: "
					+ reservation.getStatus());
		}
	}

	private ReservationResponse toResponse(Reservation reservation) {
		return new ReservationResponse(
				reservation.getId(),
				reservation.getResourceId(),
				reservation.getRequesterId(),
				reservation.getStartTime(),
				reservation.getEndTime(),
				reservation.getStatus(),
				reservation.getPurpose(),
				reservation.getExpectedAttendees(),
				reservation.getCreatedAt(),
				reservation.getUpdatedAt());
	}
}