package com.university.reservations.controller;

import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.model.ReservationStatus;
import com.university.reservations.service.ReservationService;
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
@RequestMapping("/api/v1/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse create(@RequestBody @Valid CreateReservationRequest request) {
		return reservationService.createReservation(request);
	}

	@GetMapping
	public List<ReservationResponse> list(
			@RequestParam(required = false) String requesterId,
			@RequestParam(required = false) String resourceId,
			@RequestParam(required = false) ReservationStatus status) {
		return reservationService.listReservations(requesterId, resourceId, status);
	}

	@GetMapping("/{id}")
	public ReservationResponse get(@PathVariable Long id) {
		return reservationService.getReservation(id);
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public ReservationResponse approve(@PathVariable Long id,
			@RequestBody(required = false) ApprovalRequest request) {
		return reservationService.approveReservation(id, request == null ? new ApprovalRequest(null) : request);
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('RESOURCE_MANAGER')")
	public ReservationResponse reject(@PathVariable Long id, @RequestBody ApprovalRequest request) {
		return reservationService.rejectReservation(id, request);
	}

	@PostMapping("/{id}/cancel")
	public ReservationResponse cancel(@PathVariable Long id) {
		return reservationService.cancelReservation(id);
	}
}