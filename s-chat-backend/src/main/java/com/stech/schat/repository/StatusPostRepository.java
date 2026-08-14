package com.stech.schat.repository;

import com.stech.schat.model.StatusPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StatusPostRepository extends JpaRepository<StatusPost, UUID> {

    @Query("SELECT s FROM StatusPost s WHERE s.userId = :userId AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<StatusPost> findActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT s FROM StatusPost s WHERE s.userId IN :userIds AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<StatusPost> findActiveForUsers(@Param("userIds") List<UUID> userIds, @Param("now") Instant now);
}
