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
@Table(name = "status_posts", indexes = {
        // Backs both findActiveByUser() and findActiveForUsers(), which filter on
        // user_id and expires_at on every Status tab / feed load.
        @Index(name = "idx_status_user_expires", columnList = "user_id, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPost {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Nullable now: a text-only status (Feature: text status) has no media at all.
    // Voice-note statuses reuse this same column — StorageService's audio MIME types
    // already cover them, so no schema change was needed for that case specifically.
    @Column(name = "media_url", length = 512)
    private String mediaUrl;

    @Column(length = 280)
    private String caption;

    // Text-status content — mutually exclusive with mediaUrl (exactly one of the two is
    // set; enforced in StatusService, not the DB, to keep the column nullable/simple).
    @Column(name = "text_content", length = 700)
    private String textContent;

    // Hex color (e.g. "#0aa89a") for the text-status background. Ignored for media statuses.
    @Column(name = "background_color", length = 16)
    private String backgroundColor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(24 * 60 * 60);
    }

    public boolean isTextStatus() {
        return mediaUrl == null;
    }
}
