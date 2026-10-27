package com.mtchlvr.hotel.repo;

import com.mtchlvr.hotel.model.GuestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRepository extends JpaRepository<GuestReport, Long> {
    List<GuestReport> findAllByOrderByCreatedAtDesc();
}
