package com.stech.schat.repository;

import com.stech.schat.model.MessageHiddenFor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageHiddenForRepository extends JpaRepository<MessageHiddenFor, UUID> {
    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
    Optional<MessageHiddenFor> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
