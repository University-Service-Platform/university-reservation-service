package com.university.reservations.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Group5ResponsibilityInfo(
		@JsonProperty("responsibility_id") String responsibilityId,
		@JsonProperty("role_title") String roleTitle,
		@JsonProperty("service_unit_id") String serviceUnitId,
		@JsonProperty("service_unit_name") String serviceUnitName,
		@JsonProperty("department_id") String departmentId,
		@JsonProperty("department_name") String departmentName,
		@JsonProperty("faculty_id") String facultyId,
		@JsonProperty("faculty_name") String facultyName) {
}
