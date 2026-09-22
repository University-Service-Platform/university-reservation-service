package com.university.reservations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.university.reservations.config.SecurityConfig;
import com.university.reservations.dto.ApprovalRequest;
import com.university.reservations.dto.CreateReservationRequest;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.exception.ResourceNotFoundException;
import com.university.reservations.model.ReservationStatus;
import com.university.reservations.service.ReservationService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReservationService reservationService;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	private final LocalDateTime base = LocalDateTime.of(2026, 10, 20, 9, 0);

	private final ReservationResponse response = new ReservationResponse(
			1L, "resource-1", "student-7", base, base.plusHours(2),
			ReservationStatus.PENDING, "Group study", 20, base, base);

	private String createdRequestBody() {
		return """
				{
				  "resourceId": "resource-1",
				  "requesterId": "student-7",
				  "startTime": "2026-10-20T09:00:00",
				  "endTime": "2026-10-20T11:00:00",
				  "purpose": "Group study",
				  "expectedAttendees": 20
				}
				""";
	}

	@Test
	@WithMockUser
	void create_returns201() throws Exception {
		when(reservationService.createReservation(any(CreateReservationRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/v1/reservations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(createdRequestBody()))
				.andExpect(status().isCreated());

		verify(reservationService).createReservation(any(CreateReservationRequest.class));
	}

	@Test
	@WithMockUser
	void create_returns400ForInvalidBody() throws Exception {
		String invalid = """
				{
				  "resourceId": "resource-1",
				  "requesterId": "student-7",
				  "startTime": "2026-10-20T09:00:00",
				  "endTime": "2026-10-20T11:00:00",
				  "purpose": "",
				  "expectedAttendees": 0
				}
				""";

		mockMvc.perform(post("/api/v1/reservations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(invalid))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(username = "student-7")
	void get_returns404WhenNotFound() throws Exception {
		when(reservationService.getReservation(999L)).thenThrow(new ResourceNotFoundException("Reservation not found: 999"));

		String body = mockMvc.perform(get("/api/v1/reservations/999"))
				.andExpect(status().isNotFound())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertEquals(true, body.contains("Reservation not found"));
	}

	@Test
	@WithMockUser(roles = "RESOURCE_MANAGER")
	void approve_asManagerIsAllowed() throws Exception {
		when(reservationService.approveReservation(any(Long.class), any(ApprovalRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/v1/reservations/1/approve"))
				.andExpect(status().isOk());

		verify(reservationService).approveReservation(any(Long.class), any(ApprovalRequest.class));
	}

	@Test
	@WithMockUser(roles = "STUDENT")
	void approve_asStudentIsForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/reservations/1/approve"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "RESOURCE_MANAGER")
	void reject_asManagerWithoutReasonIsBadRequest() throws Exception {
		when(reservationService.rejectReservation(any(Long.class), any(ApprovalRequest.class)))
				.thenThrow(new com.university.reservations.exception.BusinessException(
						"A written reason is mandatory when rejecting a reservation"));

		mockMvc.perform(post("/api/v1/reservations/1/reject")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "STUDENT")
	void reject_asStudentIsForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/reservations/1/reject")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\": \"Room unavailable\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void list_returns200() throws Exception {
		when(reservationService.listReservations(any(), any(), any())).thenReturn(java.util.List.of(response));

		mockMvc.perform(get("/api/v1/reservations")
						.queryParam("status", "PENDING"))
				.andExpect(status().isOk());
	}
}