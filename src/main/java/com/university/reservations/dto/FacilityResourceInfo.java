package com.university.reservations.dto;

import java.time.LocalTime;

public record FacilityResourceInfo(
		String id,
		String name,
		Integer capacity,
		LocalTime opensAt,
		LocalTime closesAt) {
}