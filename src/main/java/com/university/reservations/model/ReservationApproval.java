package com.university.reservations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_approvals")
public class ReservationApproval {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reservation_id", nullable = false)
	private Reservation reservation;

	@Column(nullable = false, length = 64)
	private String actionBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReservationApprovalAction action;

	@Column(length = 500)
	private String reason;

	@Column(nullable = false)
	private LocalDateTime actionTimestamp;

	@PrePersist
	void onCreate() {
		this.actionTimestamp = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Reservation getReservation() {
		return reservation;
	}

	public void setReservation(Reservation reservation) {
		this.reservation = reservation;
	}

	public String getActionBy() {
		return actionBy;
	}

	public void setActionBy(String actionBy) {
		this.actionBy = actionBy;
	}

	public ReservationApprovalAction getAction() {
		return action;
	}

	public void setAction(ReservationApprovalAction action) {
		this.action = action;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public LocalDateTime getActionTimestamp() {
		return actionTimestamp;
	}

	public void setActionTimestamp(LocalDateTime actionTimestamp) {
		this.actionTimestamp = actionTimestamp;
	}
}