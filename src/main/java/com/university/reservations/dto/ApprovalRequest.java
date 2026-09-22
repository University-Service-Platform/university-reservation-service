package com.university.reservations.dto;

import jakarta.validation.constraints.Size;

public record ApprovalRequest(
		@Size(max = 500) String reason) {
}