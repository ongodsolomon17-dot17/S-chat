package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = {
        // findConversation() filters/sorts on exactly these columns for every history
        // load and every WebSocket send — without this the DB does a full table scan
        // that gets slower as message volume grows.
        @Index(name = "idx_chat_sender_receiver_sent", columnList = "sender_id, receiver_id, sent_at"),
        @Index(name = "idx_chat_receiver_sender_sent", columnList = "receiver_id, sender_id, sent_at"),
        // Backs the reply-preview lookup (fetching the parent message a reply points to).
        @Index(name = "idx_chat_reply_to", columnList = "reply_to_message_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue
    private UUID id;

    // No FK cascade-delete: if a user soft-deletes their account, these rows
    // and their content are left exactly as they are — only the User row
    // referenced by these ids gets flagged `deleted`.
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false)
    private UUID receiverId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url", length = 512)
    private String attachmentUrl;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    // Self-referencing, no FK cascade (same reasoning as sender/receiver above) — kept as a
    // plain UUID rather than a JPA @ManyToOne so a reply never triggers a fetch of the whole
    // parent message unless we explicitly ask for one (see ChatService reply-preview loading).
    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    // Set when this message is a reply sent from a status viewer rather than the chat itself.
    // Reuses the same chat pipeline (Feature Group C requirement) instead of a parallel system.
    @Column(name = "reply_to_status_id")
    private UUID replyToStatusId;

    // Soft delete. `deletedAt` is the source of truth for "is this message hidden";
    // `deletedBy` is retained for audit/debugging even though only the sender is
    // currently allowed to delete their own message.
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @PrePersist
    void onCreate() {
        this.sentAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
