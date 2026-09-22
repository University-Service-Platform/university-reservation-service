package com.university.reservations.repository;

import com.university.reservations.model.Reservation;
import com.university.reservations.model.ReservationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	@Query("SELECT COUNT(r) FROM Reservation r WHERE r.resourceId = :resourceId "
			+ "AND r.status = com.university.reservations.model.ReservationStatus.APPROVED "
			+ "AND (r.startTime < :endTime AND r.endTime > :startTime)")
	long countOverlappingApproved(@Param("resourceId") String resourceId,
			@Param("startTime") LocalDateTime startTime,
			@Param("endTime") LocalDateTime endTime);

	List<Reservation> findByRequesterId(String requesterId);

	List<Reservation> findByResourceId(String resourceId);

	List<Reservation> findByStatus(ReservationStatus status);
}