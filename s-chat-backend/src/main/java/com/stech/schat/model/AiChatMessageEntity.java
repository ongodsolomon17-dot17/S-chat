package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages", indexes = {
        @Index(name = "idx_ai_messages_conversation_created", columnList = "conversation_id,created_at"),
        @Index(name = "idx_ai_messages_client_id", columnList = "conversation_id,client_message_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_message_client_id", columnNames = {"conversation_id", "client_message_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiChatMessageEntity {
    @Id @GeneratedValue
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "client_message_id", length = 80)
    private String clientMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }
}
