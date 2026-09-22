package com.university.reservations.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	public String getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			throw new IllegalStateException("No authenticated user");
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof Jwt jwt) {
			return jwt.getSubject();
		}
		return authentication.getName();
	}
}