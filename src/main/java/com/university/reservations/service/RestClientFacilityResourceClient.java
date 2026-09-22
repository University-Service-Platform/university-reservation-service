package com.university.reservations.service;

import com.university.reservations.dto.FacilityResourceInfo;
import com.university.reservations.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class RestClientFacilityResourceClient implements FacilityResourceClient {

	private final RestClient restClient;

	public RestClientFacilityResourceClient(@Value("${facility-resource-service.base-url}") String baseUrl) {
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public FacilityResourceInfo getResource(String resourceId) {
		try {
			return restClient.get()
					.uri("/api/v1/resources/{id}", resourceId)
					.retrieve()
					.body(FacilityResourceInfo.class);
		} catch (HttpClientErrorException.NotFound ex) {
			throw new ResourceNotFoundException("Facility resource not found: " + resourceId);
		}
	}
}