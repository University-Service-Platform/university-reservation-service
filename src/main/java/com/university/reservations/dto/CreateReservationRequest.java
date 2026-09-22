package com.university.reservations.dto;

import com.university.reservations.exception.BusinessException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record CreateReservationRequest(
		@NotBlank @Size(max = 64) String resourceId,
		@NotBlank @Size(max = 64) String requesterId,
		@NotNull LocalDateTime startTime,
		@NotNull LocalDateTime endTime,
		@NotBlank @Size(max = 255) String purpose,
		@NotNull @Min(1) Integer expectedAttendees) {

	public void validate() {
		if (!startTime.isBefore(endTime)) {
			throw new BusinessException("startTime must be before endTime");
		}
	}
}