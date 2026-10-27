package com.mtchlvr.hotel.repo;

import com.mtchlvr.hotel.model.StayRating;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StayRatingRepository extends JpaRepository<StayRating, Long> {
    Optional<StayRating> findByReservation_Id(Long reservationId);
    List<StayRating> findAllByOrderByCreatedAtDesc();
}
