package com.stech.schat.repository;

import com.stech.schat.model.CallRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface CallRecordRepository extends JpaRepository<CallRecord, UUID> {
    @Query("""
        SELECT c FROM CallRecord c
        WHERE c.callerId = :userId OR c.calleeId = :userId
        ORDER BY c.startedAt DESC
        """)
    List<CallRecord> findHistory(@Param("userId") UUID userId, Pageable pageable);

    List<CallRecord> findByStatusAndStartedAtBefore(CallRecord.CallStatus status, Instant before);
}
