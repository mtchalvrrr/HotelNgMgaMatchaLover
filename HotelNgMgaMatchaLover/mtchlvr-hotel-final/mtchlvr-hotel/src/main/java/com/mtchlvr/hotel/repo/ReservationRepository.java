package com.mtchlvr.hotel.repo;
import com.mtchlvr.hotel.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByCustomerEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    List<Reservation> findAllByOrderByCreatedAtDesc();
}
