package com.stech.schat.repository;

import com.stech.schat.model.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    Optional<AiConversation> findByUserId(UUID userId);
}
