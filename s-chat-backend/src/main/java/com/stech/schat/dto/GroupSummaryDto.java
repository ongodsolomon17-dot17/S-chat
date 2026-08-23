package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupSummaryDto(
        UUID id,
        String name,
        String avatarUrl,
        UUID createdBy,
        Instant createdAt,
        String myRole,
        int memberCount
) {}
