package com.stech.schat.repository;

import com.stech.schat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userA AND m.receiverId = :userB)
           OR (m.senderId = :userB AND m.receiverId = :userA)
        ORDER BY m.sentAt DESC
        """)
    Page<ChatMessage> findConversation(@Param("userA") UUID userA, @Param("userB") UUID userB, Pageable pageable);
}
