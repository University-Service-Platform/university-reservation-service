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
import com.university.reservations.dto.ReservationApprovalResponse;
import com.university.reservations.dto.ReservationResponse;
import com.university.reservations.dto.ReservationStatusSummaryResponse;
import com.university.reservations.dto.ResourceReservationSummaryResponse;
import com.university.reservations.dto.UsageTrendResponse;
import com.university.reservations.exception.BusinessException;
import com.university.reservations.exception.ResourceNotFoundException;
import com.university.reservations.model.ReservationApprovalAction;
import com.university.reservations.model.ReservationStatus;
import com.university.reservations.service.ReservationService;
import java.time.LocalDateTime;
import java.util.List;
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

	private final LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);

	private final ReservationResponse response = new ReservationResponse(
			1L, "1", "student-7", base, base.plusHours(2),
			ReservationStatus.PENDING, "Group study", 20, base, base);

	private String createdRequestBody() {
		return """
				{
				  "resourceId": "1",
				  "requesterId": "student-7",
				  "startTime": "%s",
				  "endTime": "%s",
				  "purpose": "Group study",
				  "expectedAttendees": 20
				}
				""".formatted(base.toString(), base.plusHours(2).toString());
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
				  "resourceId": "1",
				  "requesterId": "student-7",
				  "startTime": "%s",
				  "endTime": "%s",
				  "purpose": "",
				  "expectedAttendees": 0
				}
				""".formatted(base.toString(), base.plusHours(2).toString());

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
				.thenThrow(new BusinessException("A written reason is mandatory when rejecting a reservation"));

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
		when(reservationService.listReservations(any(), any(), any())).thenReturn(List.of(response));

		mockMvc.perform(get("/api/v1/reservations")
						.queryParam("status", "PENDING"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = "RESOURCE_MANAGER")
	void getPending_asManagerReturns200() throws Exception {
		when(reservationService.getPendingReservations()).thenReturn(List.of(response));

		mockMvc.perform(get("/api/v1/reservations/pending"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void getMyReservations_returns200() throws Exception {
		when(reservationService.getMyReservations()).thenReturn(List.of(response));

		mockMvc.perform(get("/api/v1/reservations/my"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void getHistory_returns200() throws Exception {
		ReservationApprovalResponse history = new ReservationApprovalResponse(
				10L, 1L, "manager-1", ReservationApprovalAction.APPROVED, "Ok", base);
		when(reservationService.getReservationHistory(1L)).thenReturn(List.of(history));

		mockMvc.perform(get("/api/v1/reservations/1/history"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void getStatusSummary_returns200() throws Exception {
		when(reservationService.getStatusSummary()).thenReturn(new ReservationStatusSummaryResponse(1, 2, 0, 1));

		mockMvc.perform(get("/api/v1/reservations/summary/status"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void getResourceSummaries_returns200() throws Exception {
		when(reservationService.getResourceSummaries()).thenReturn(List.of(new ResourceReservationSummaryResponse("1", 5, 4, 1)));

		mockMvc.perform(get("/api/v1/reservations/summary/resources"))
				.andExpect(status().isOk());
	}

	@Test
	@WithMockUser
	void getResourceTrend_returns200() throws Exception {
		when(reservationService.getResourceTrend("1")).thenReturn(List.of(new UsageTrendResponse("2026-10-20", 3)));

		mockMvc.perform(get("/api/v1/reservations/summary/resources/1/trend"))
				.andExpect(status().isOk());
	}
}