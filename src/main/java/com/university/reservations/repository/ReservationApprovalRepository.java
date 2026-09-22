package com.university.reservations.repository;

import com.university.reservations.model.ReservationApproval;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationApprovalRepository extends JpaRepository<ReservationApproval, Long> {

	List<ReservationApproval> findByReservationId(Long reservationId);
}