package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.FacilityResourceValidationWrapper;
import com.university.reservations.dto.ResourceAvailabilityCheckRequest;
import com.university.reservations.exception.BusinessException;
import com.university.reservations.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientFacilityResourceClient implements FacilityResourceClient {

	private static final Logger log = LoggerFactory.getLogger(RestClientFacilityResourceClient.class);

	private final RestClient restClient;

	public RestClientFacilityResourceClient(@Value("${facility-resource-service.base-url:http://localhost:8081}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public FacilityResourceValidationData validateResource(String resourceId) {
		try {
			FacilityResourceValidationWrapper response = restClient.get()
					.uri("/api/resources/{id}/validate", resourceId)
					.retrieve()
					.body(FacilityResourceValidationWrapper.class);

			if (response == null || response.data() == null) {
				throw new BusinessException("Received empty or malformed response from facility-resource-service for resource: " + resourceId);
			}

			FacilityResourceValidationData data = response.data();
			if (Boolean.FALSE.equals(data.exists())) {
				throw new ResourceNotFoundException("Resource does not exist: " + resourceId);
			}
			if (Boolean.FALSE.equals(data.active())) {
				throw new BusinessException("Resource is inactive: " + resourceId);
			}
			if (Boolean.FALSE.equals(data.available())) {
				throw new BusinessException("Resource is currently unavailable for reservation: " + resourceId);
			}
			if (Boolean.FALSE.equals(data.validForReservation())) {
				String msg = data.message() != null ? data.message() : "Resource is not valid for reservation";
				throw new BusinessException("Resource invalid for reservation: " + msg);
			}

			return data;
		} catch (HttpClientErrorException.NotFound ex) {
			throw new ResourceNotFoundException("Facility resource not found: " + resourceId);
		} catch (HttpClientErrorException | org.springframework.web.client.HttpServerErrorException ex) {
			log.error("Facility resource service HTTP error for resource {}: {}", resourceId, ex.getMessage());
			throw new BusinessException("Facility resource service error (" + ex.getStatusCode() + "): " + ex.getStatusText());
		} catch (RestClientException ex) {
			log.error("Failed to connect to facility-resource-service: {}", ex.getMessage());
			throw new BusinessException("Facility resource service connection failed or timed out: " + ex.getMessage());
		}
	}

	@Override
	public boolean checkAvailability(String resourceId, LocalDateTime startTime, LocalDateTime endTime) {
		try {
			ResourceAvailabilityCheckRequest request = new ResourceAvailabilityCheckRequest(resourceId, startTime, endTime);
			FacilityResourceValidationWrapper response = restClient.post()
					.uri("/api/resources/check-availability")
					.body(request)
					.retrieve()
					.body(FacilityResourceValidationWrapper.class);

			if (response == null || response.data() == null) {
				return false;
			}
			return Boolean.TRUE.equals(response.data().available());
		} catch (Exception ex) {
			log.warn("Availability check endpoint failed for resource {}: {}", resourceId, ex.getMessage());
			return true; // Fallback to validation check
		}
	}
}