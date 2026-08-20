package com.stech.schat.repository;

import com.stech.schat.model.AiChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, UUID> {
    List<AiChatMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);
    List<AiChatMessageEntity> findTop24ByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);
    Optional<AiChatMessageEntity> findByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);
}
