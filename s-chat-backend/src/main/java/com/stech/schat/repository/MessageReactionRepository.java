package com.stech.schat.repository;

import com.stech.schat.model.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, UUID> {

    Optional<MessageReaction> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<MessageReaction> findByMessageId(UUID messageId);

    // Batch load for a whole conversation/chat-list page in one query instead of N+1.
    List<MessageReaction> findByMessageIdIn(List<UUID> messageIds);

    @Modifying
    @Query("DELETE FROM MessageReaction r WHERE r.messageId = :messageId AND r.userId = :userId")
    void deleteByMessageIdAndUserId(@Param("messageId") UUID messageId, @Param("userId") UUID userId);
}
