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
@Table(name = "friend_requests", uniqueConstraints = {
        // Prevents duplicate pending/accepted request rows between the same pair
        @UniqueConstraint(columnNames = {"requester_id", "addressee_id"})
}, indexes = {
        // Backs listPendingIncoming() and the "reverse pending request" lookup in
        // FriendService.sendRequest() — both filter by addressee_id (+status).
        @Index(name = "idx_friend_req_addressee_status", columnList = "addressee_id, status"),
        // Backs the reverse-direction and areFriends() lookups keyed off requester_id.
        @Index(name = "idx_friend_req_requester", columnList = "requester_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private FriendRequestStatus status = FriendRequestStatus.PENDING;

    // How the requester found the addressee — kept for auditing/support, not shown to users
    @Column(name = "matched_via", length = 16)
    private String matchedVia; // "ID" or "CONTACT"

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
