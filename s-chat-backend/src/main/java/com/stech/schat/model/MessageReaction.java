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
 * A relational join table rather than a JSON blob on ChatMessage: reactions are
 * many-per-message, many-per-user, need a hard uniqueness guarantee (one reaction
 * per user per message — re-reacting updates, it doesn't stack), and need fast
 * "all reactions for these N messages" lookups for chat history/list loads.
 */
@Entity
@Table(name = "chat_message_reactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Kept as a short string rather than an enum column so the allowed reaction set
    // (currently ❤️ 😂 👍 😮 😢 🙏) can grow without a migration.
    @Column(name = "reaction_type", nullable = false, length = 32)
    private String reactionType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
