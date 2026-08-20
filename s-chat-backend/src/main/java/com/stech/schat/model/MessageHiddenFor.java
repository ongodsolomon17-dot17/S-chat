package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * "Delete for me" — a per-viewer hide flag, separate from ChatMessage.deletedAt (which is
 * "delete for everyone"). The message row itself, and the other participant's copy of it,
 * are untouched; this just excludes it from *this* user's history/list queries.
 */
@Entity
@Table(name = "chat_message_hidden_for")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageHiddenFor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "hidden_at", nullable = false, updatable = false)
    private Instant hiddenAt;

    @PrePersist
    void onCreate() {
        this.hiddenAt = Instant.now();
    }
}
