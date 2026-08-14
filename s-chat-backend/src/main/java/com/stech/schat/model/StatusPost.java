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
@Table(name = "status_posts")
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

    @Column(name = "media_url", nullable = false, length = 512)
    private String mediaUrl;

    @Column(length = 280)
    private String caption;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plusSeconds(24 * 60 * 60);
    }
}
