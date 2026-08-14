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
@Table(name = "app_users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "public_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    // The user-facing "S-Chat ID" used only for adding friends — separate from the
    // internal UUID primary key so it's safe to show and safe to let users edit.
    @Column(name = "public_id", nullable = false, length = 32)
    private String publicId;

    @Column(name = "profile_picture_url", length = 512)
    private String profilePictureUrl;

    // Contact (phone/email) lookup is the default; when true, only publicId adds are allowed.
    @Column(name = "add_by_id_only", nullable = false)
    @Builder.Default
    private boolean addByIdOnly = false;

    // When true, incoming friend requests need this user to accept/decline.
    // When false, requests are auto-accepted.
    @Column(name = "approval_required", nullable = false)
    @Builder.Default
    private boolean approvalRequired = true;

    // BCrypt hash only — plaintext passwords are never stored or logged
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "account_enabled", nullable = false)
    @Builder.Default
    private boolean accountEnabled = true;

    // Soft-delete: disables login and hides the user from search/add, but
    // existing messages and friendships that reference this id are untouched.
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
