package com.university.reservations.controller;

import com.university.reservations.dto.ApiResponse;
import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.ReservationApprovalResponse;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.dto.ReservationStatusSummaryResponse;
import com.university.reservations.dto.ResourceReservationSummaryResponse;
import com.university.reservations.dto.UsageTrendResponse;
import com.university.reservations.model.ReservationStatus;
import com.university.reservations.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/reservations", "/api/reservations"})
@Tag(name = "Reservations", description = "University Facility Reservation API Management")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a new reservation", description = "Validates resource validation and availability via Group 6 facility-resource-service before saving.")
	public ApiResponse<ReservationResponse> create(@RequestBody @Valid CreateReservationRequest request) {
		return ApiResponse.success("Reservation created successfully", reservationService.createReservation(request));
	}

	@GetMapping
	@Operation(summary = "List reservations", description = "Filters reservations by requester, resource, or status.")
	public ApiResponse<List<ReservationResponse>> list(
			@RequestParam(required = false) String requesterId,
			@RequestParam(required = false) Long resourceId,
			@RequestParam(required = false) ReservationStatus status) {
		return ApiResponse.success("Reservations retrieved successfully", reservationService.listReservations(requesterId, resourceId, status));
	}

	@GetMapping("/pending")
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	@Operation(summary = "View pending reservation requests", description = "Restricted to Resource Managers. Returns all reservations awaiting approval.")
	public ApiResponse<List<ReservationResponse>> getPending() {
		return ApiResponse.success("Pending reservations retrieved successfully", reservationService.getPendingReservations());
	}

	@GetMapping("/my")
	@Operation(summary = "View my reservations", description = "Returns reservations created by the currently authenticated user.")
	public ApiResponse<List<ReservationResponse>> getMyReservations() {
		return ApiResponse.success("User reservations retrieved successfully", reservationService.getMyReservations());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get reservation details", description = "Fetches a single reservation by ID.")
	public ApiResponse<ReservationResponse> get(@PathVariable Long id) {
		return ApiResponse.success("Reservation details retrieved successfully", reservationService.getReservation(id));
	}

	@GetMapping("/{id}/history")
	@Operation(summary = "View reservation history", description = "Returns chronological approval and rejection audit logs for a reservation.")
	public ApiResponse<List<ReservationApprovalResponse>> getHistory(@PathVariable Long id) {
		return ApiResponse.success("Reservation history retrieved successfully", reservationService.getReservationHistory(id));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	@Operation(summary = "Approve a pending reservation", description = "Restricted to Resource Managers. Approves a PENDING reservation and records audit log.")
	public ApiResponse<ReservationResponse> approve(@PathVariable Long id,
			@RequestBody(required = false) ApprovalRequest request) {
		return ApiResponse.success("Reservation approved successfully", reservationService.approveReservation(id, request == null ? new ApprovalRequest(null) : request));
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	@Operation(summary = "Reject a pending reservation", description = "Restricted to Resource Managers. Rejects a PENDING reservation with a mandatory reason.")
	public ApiResponse<ReservationResponse> reject(@PathVariable Long id, @RequestBody ApprovalRequest request) {
		return ApiResponse.success("Reservation rejected successfully", reservationService.rejectReservation(id, request));
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel a reservation", description = "Cancels a PENDING or APPROVED reservation.")
	public ApiResponse<ReservationResponse> cancel(@PathVariable Long id) {
		return ApiResponse.success("Reservation cancelled successfully", reservationService.cancelReservation(id));
	}

	@GetMapping("/summary/status")
	@Operation(summary = "View reservation status summary", description = "Returns aggregate count of reservations grouped by status.")
	public ApiResponse<ReservationStatusSummaryResponse> getStatusSummary() {
		return ApiResponse.success("Status summary retrieved successfully", reservationService.getStatusSummary());
	}

	@GetMapping("/summary/resources")
	@Operation(summary = "View resource utilization summary", description = "Returns reservation counts grouped by resource.")
	public ApiResponse<List<ResourceReservationSummaryResponse>> getResourceSummaries() {
		return ApiResponse.success("Resource summaries retrieved successfully", reservationService.getResourceSummaries());
	}

	@GetMapping("/summary/resources/{resourceId}/trend")
	@Operation(summary = "View usage trends by resource", description = "Returns daily usage trend count for a specific resource.")
	public ApiResponse<List<UsageTrendResponse>> getResourceTrend(@PathVariable Long resourceId) {
		return ApiResponse.success("Resource trend retrieved successfully", reservationService.getResourceTrend(resourceId));
	}
}
