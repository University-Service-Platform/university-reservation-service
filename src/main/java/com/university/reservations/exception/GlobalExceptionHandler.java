package com.university.reservations.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.collect(Collectors.joining(", "));
		return build(HttpStatus.BAD_REQUEST.value(), message, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST.value(), "Invalid value for parameter: " + ex.getName(), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST.value(), "Malformed request body", request);
	}

	@ExceptionHandler(ReservationConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ReservationConflictException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT.value(), ex.getMessage(), request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN.value(), "Access denied", request);
	}

	@ExceptionHandler(org.springframework.security.authorization.AuthorizationDeniedException.class)
	public ResponseEntity<ApiError> handleAuthorizationDenied(
			org.springframework.security.authorization.AuthorizationDeniedException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN.value(), "Access denied", request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
		return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred", request);
	}

	private ResponseEntity<ApiError> build(int status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(new ApiError(Instant.now(), status, message, request.getRequestURI()));
	}
}