package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of a user accepting a specific version of the Terms and Privacy Policy. */
@Entity
@Table(name = "terms_acceptances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsAcceptance {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "terms_version", nullable = false, length = 32)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false, length = 32)
    private String privacyVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @PrePersist
    void onCreate() {
        this.acceptedAt = Instant.now();
    }
}
