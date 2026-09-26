package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.ResourceAvailabilityData;
import java.time.LocalDateTime;

public interface FacilityResourceClient {

	FacilityResourceValidationData validateResource(Long resourceId);

	ResourceAvailabilityData checkAvailability(Long resourceId, LocalDateTime startTime, LocalDateTime endTime, Integer requestedCapacity, String userRole);
}