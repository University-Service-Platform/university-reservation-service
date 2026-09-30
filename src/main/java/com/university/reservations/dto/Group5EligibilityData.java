package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Group5EligibilityData(
		@JsonProperty("user_id") String userId,
		@JsonProperty("university_id") String universityId,
		@JsonProperty("account_status") String accountStatus,
		List<String> roles,
		Boolean eligible,
		List<String> reasons,
		String message,
		Group5EligibilityChecks checks,
		@JsonProperty("matched_responsibilities") List<Group5ResponsibilityInfo> matchedResponsibilities,
		Group5AffiliationInfo affiliation) {
}
