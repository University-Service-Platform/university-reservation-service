package com.university.reservations.dto;

import com.university.reservations.exception.BusinessException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateReservationRequest(
		@NotNull Long resourceId,
		@Size(max = 64) String requesterId,
		@NotNull LocalDateTime startTime,
		@NotNull LocalDateTime endTime,
		@NotBlank @Size(max = 255) String purpose,
		@NotNull @Min(1) Integer expectedAttendees) {

	public void validate() {
		if (startTime == null || endTime == null) {
			throw new BusinessException("startTime and endTime are required");
		}
		if (!startTime.isBefore(endTime)) {
			throw new BusinessException("startTime must be before endTime");
		}
		if (startTime.isBefore(LocalDateTime.now())) {
			throw new BusinessException("startTime must be in the future");
		}
	}
}