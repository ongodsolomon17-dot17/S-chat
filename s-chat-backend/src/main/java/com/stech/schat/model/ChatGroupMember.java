package com.stech.schat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_group_members", uniqueConstraints = @UniqueConstraint(name = "uk_chat_group_member", columnNames = {"group_id", "user_id"}), indexes = {
        @Index(name = "idx_group_members_user", columnList = "user_id"),
        @Index(name = "idx_group_members_group", columnList = "group_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatGroupMember {
    public enum MemberRole { ADMIN, MEMBER }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        joinedAt = Instant.now();
    }
}
