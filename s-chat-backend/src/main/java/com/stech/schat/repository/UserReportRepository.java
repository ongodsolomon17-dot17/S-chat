package com.stech.schat.repository;

import com.stech.schat.model.UserReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserReportRepository extends JpaRepository<UserReport, UUID> {
    List<UserReport> findByStatusOrderByCreatedAtDesc(UserReport.Status status, Pageable pageable);

    long countByStatus(UserReport.Status status);

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);
}
