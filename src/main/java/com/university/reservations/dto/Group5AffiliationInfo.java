package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Group5AffiliationInfo(
		@JsonProperty("department_id") String departmentId,
		@JsonProperty("department_name") String departmentName,
		@JsonProperty("faculty_id") String facultyId,
		@JsonProperty("faculty_name") String facultyName) {
}
