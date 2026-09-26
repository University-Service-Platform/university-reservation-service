package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceValidationData;
import com.university.reservations.dto.FacilityResourceValidationWrapper;
import com.university.reservations.dto.ResourceAvailabilityCheckRequest;
import com.university.reservations.dto.ResourceAvailabilityData;
import com.university.reservations.dto.ResourceAvailabilityWrapper;
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
	public FacilityResourceValidationData validateResource(Long resourceId) {
		try {
			FacilityResourceValidationWrapper response = restClient.get()
					.uri("/api/resources/{id}/validate", resourceId)
					.retrieve()
					.body(FacilityResourceValidationWrapper.class);

			if (response == null || response.data() == null) {
				throw new BusinessException("Received empty or malformed validation response from facility-resource-service for resource: " + resourceId);
			}

			FacilityResourceValidationData data = response.data();

			// CRITICAL: Validation endpoint returns HTTP 200 even when resource does not exist.
			if (Boolean.FALSE.equals(data.exists())) {
				String msg = data.message() != null ? data.message() : "Resource with ID " + resourceId + " does not exist";
				throw new ResourceNotFoundException(msg);
			}

			if (Boolean.FALSE.equals(data.active())) {
				throw new BusinessException("Resource is inactive: " + resourceId);
			}

			if (Boolean.FALSE.equals(data.validForReservation())) {
				String msg = data.message() != null ? data.message() : "Resource is not valid for reservation";
				throw new BusinessException("Resource invalid for reservation: " + msg);
			}

			return data;
		} catch (BusinessException ex) {
			throw ex;
		} catch (HttpClientErrorException.NotFound ex) {
			throw new ResourceNotFoundException("Resource with ID " + resourceId + " does not exist");
		} catch (HttpClientErrorException | org.springframework.web.client.HttpServerErrorException ex) {
			log.error("Facility resource service HTTP error for resource {}: {}", resourceId, ex.getMessage());
			throw new BusinessException("Facility resource service error (" + ex.getStatusCode() + "): " + ex.getStatusText());
		} catch (RestClientException ex) {
			log.error("Failed to connect to facility-resource-service: {}", ex.getMessage());
			throw new BusinessException("Facility resource service connection failed or timed out: " + ex.getMessage());
		}
	}

	@Override
	public ResourceAvailabilityData checkAvailability(Long resourceId, LocalDateTime startTime, LocalDateTime endTime, Integer requestedCapacity, String userRole) {
		try {
			ResourceAvailabilityCheckRequest request = new ResourceAvailabilityCheckRequest(
					resourceId,
					startTime.toLocalDate(),
					startTime.toLocalTime(),
					endTime.toLocalTime(),
					requestedCapacity,
					userRole);

			ResourceAvailabilityWrapper response = restClient.post()
					.uri("/api/resources/check-availability")
					.body(request)
					.retrieve()
					.body(ResourceAvailabilityWrapper.class);

			if (response == null || response.data() == null) {
				throw new BusinessException("Received empty or malformed availability response from facility-resource-service for resource: " + resourceId);
			}

			return response.data();
		} catch (BusinessException ex) {
			throw ex;
		} catch (HttpClientErrorException.NotFound ex) {
			throw new ResourceNotFoundException("Resource with ID " + resourceId + " does not exist");
		} catch (HttpClientErrorException | org.springframework.web.client.HttpServerErrorException ex) {
			log.error("Facility resource availability check HTTP error for resource {}: {}", resourceId, ex.getMessage());
			throw new BusinessException("Facility resource availability check failed (" + ex.getStatusCode() + "): " + ex.getStatusText());
		} catch (RestClientException ex) {
			log.error("Failed to connect to facility-resource-service availability check: {}", ex.getMessage());
			throw new BusinessException("Facility resource service connection failed or timed out: " + ex.getMessage());
		}
	}
}