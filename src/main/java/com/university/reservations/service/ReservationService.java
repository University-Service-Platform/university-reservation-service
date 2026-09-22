package com.university.reservations.service;

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
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final ReservationApprovalRepository approvalRepository;
	private final FacilityResourceClient facilityResourceClient;
	private final AuthService authService;

	public ReservationService(ReservationRepository reservationRepository,
			ReservationApprovalRepository approvalRepository,
			FacilityResourceClient facilityResourceClient,
			AuthService authService) {
		this.reservationRepository = reservationRepository;
		this.approvalRepository = approvalRepository;
		this.facilityResourceClient = facilityResourceClient;
		this.authService = authService;
	}

	@Transactional
	public ReservationResponse createReservation(CreateReservationRequest request) {
		request.validate();

		FacilityResourceInfo resource = facilityResourceClient.getResource(request.resourceId());

		if (resource.capacity() != null && request.expectedAttendees() > resource.capacity()) {
			throw new BusinessException("expectedAttendees exceeds resource capacity of " + resource.capacity());
		}
		validateWithinOpenHours(resource, request.startTime(), request.endTime());

		Reservation reservation = new Reservation();
		reservation.setResourceId(request.resourceId());
		reservation.setRequesterId(request.requesterId());
		reservation.setStartTime(request.startTime());
		reservation.setEndTime(request.endTime());
		reservation.setStatus(ReservationStatus.PENDING);
		reservation.setPurpose(request.purpose());
		reservation.setExpectedAttendees(request.expectedAttendees());

		return toResponse(reservationRepository.save(reservation));
	}

	@Transactional(readOnly = true)
	public ReservationResponse getReservation(Long id) {
		return toResponse(findReservation(id));
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listReservations(String requesterId, String resourceId, ReservationStatus status) {
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

	@Transactional
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public ReservationResponse approveReservation(Long id, ApprovalRequest request) {
		Reservation reservation = findReservation(id);
		ensurePending(reservation);

		long overlaps = reservationRepository.countOverlappingApproved(
				reservation.getResourceId(), reservation.getStartTime(), reservation.getEndTime());
		if (overlaps > 0) {
			throw new ReservationConflictException("Resource is already booked for the requested time");
		}

		reservation.setStatus(ReservationStatus.APPROVED);
		reservationRepository.save(reservation);
		recordApproval(reservation, ReservationApprovalAction.APPROVED, request == null ? null : request.reason());

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
		recordApproval(reservation, ReservationApprovalAction.REJECTED, request.reason());

		return toResponse(reservation);
	}

	@Transactional
	public ReservationResponse cancelReservation(Long id) {
		Reservation reservation = findReservation(id);
		if (reservation.getStatus() == ReservationStatus.CANCELLED) {
			throw new BusinessException("Reservation is already cancelled");
		}
		if (reservation.getStatus() != ReservationStatus.PENDING
				&& reservation.getStatus() != ReservationStatus.APPROVED) {
			throw new BusinessException("Only PENDING or APPROVED reservations can be cancelled");
		}

		reservation.setStatus(ReservationStatus.CANCELLED);
		return toResponse(reservationRepository.save(reservation));
	}

	private void recordApproval(Reservation reservation, ReservationApprovalAction action, String reason) {
		ReservationApproval approval = new ReservationApproval();
		approval.setReservation(reservation);
		approval.setActionBy(authService.getCurrentUserId());
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

	private void validateWithinOpenHours(FacilityResourceInfo resource, LocalDateTime start, LocalDateTime end) {
		if (resource.opensAt() == null || resource.closesAt() == null) {
			return;
		}
		LocalTime opensAt = resource.opensAt();
		LocalTime closesAt = resource.closesAt();
		LocalTime startTime = start.toLocalTime();
		LocalTime endTime = end.toLocalTime();

		boolean within = (closesAt.isAfter(opensAt) || closesAt.equals(opensAt))
				? !startTime.isBefore(opensAt) && !endTime.isAfter(closesAt)
				: !startTime.isBefore(opensAt) || !endTime.isAfter(closesAt);

		if (!within) {
			throw new BusinessException("Requested time is outside facility operating hours ("
					+ opensAt + " - " + closesAt + ")");
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