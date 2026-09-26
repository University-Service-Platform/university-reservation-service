package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceValidationData;
import java.time.LocalDateTime;

public interface FacilityResourceClient {

	FacilityResourceValidationData validateResource(String resourceId);

	boolean checkAvailability(String resourceId, LocalDateTime startTime, LocalDateTime endTime);
}