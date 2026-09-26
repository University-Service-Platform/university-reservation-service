package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Group5EligibilityChecks(
		@JsonProperty("account_active") Boolean accountActive,
		@JsonProperty("required_role") String requiredRole,
		@JsonProperty("role_held") Boolean roleHeld,
		String relationship,
		@JsonProperty("relationship_satisfied") Boolean relationshipSatisfied) {
}
