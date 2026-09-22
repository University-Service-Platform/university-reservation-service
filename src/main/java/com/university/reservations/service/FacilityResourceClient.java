package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceInfo;

public interface FacilityResourceClient {

	FacilityResourceInfo getResource(String resourceId);
}