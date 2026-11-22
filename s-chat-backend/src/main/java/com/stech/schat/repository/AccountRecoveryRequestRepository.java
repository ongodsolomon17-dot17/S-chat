package com.stech.schat.repository;

import com.stech.schat.model.AccountRecoveryRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRecoveryRequestRepository extends JpaRepository<AccountRecoveryRequest, UUID> {

    Optional<AccountRecoveryRequest> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<AccountRecoveryRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);

    @Modifying
    @Query("UPDATE AccountRecoveryRequest r SET r.consumedAt = :now WHERE r.userId = :userId AND r.consumedAt IS NULL")
    int consumeAllActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM AccountRecoveryRequest r WHERE r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
