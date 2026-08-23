package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_group_messages", indexes = {
        @Index(name = "idx_group_messages_group_sent", columnList = "group_id, sent_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatGroupMessage {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url", length = 512)
    private String attachmentUrl;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @PrePersist
    void onCreate() {
        sentAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }
}
