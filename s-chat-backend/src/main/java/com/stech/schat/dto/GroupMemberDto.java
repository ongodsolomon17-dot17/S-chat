package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberDto(
        UUID userId,
        String username,
        String publicId,
        String profilePictureUrl,
        String role,
        Instant joinedAt
) {}
