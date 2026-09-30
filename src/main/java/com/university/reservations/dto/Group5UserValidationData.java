package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Group5UserValidationData(
		@JsonProperty("user_id") String userId,
		@JsonProperty("university_id") String universityId,
		String name,
		@JsonProperty("account_type") String accountType,
		String status,
		@JsonProperty("is_valid") Boolean isValid,
		List<String> roles,
		@JsonProperty("is_authorized") Boolean isAuthorized,
		@JsonProperty("required_role_checked") String requiredRoleChecked) {
}
