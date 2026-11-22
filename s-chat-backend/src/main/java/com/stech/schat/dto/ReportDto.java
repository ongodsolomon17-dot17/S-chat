package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record ReportDto(
        UUID id,
        UUID reporterId,
        String reporterUsername,
        UUID reportedUserId,
        String reportedUsername,
        UUID messageId,
        String messageExcerpt,
        String reason,
        String details,
        String status,
        Instant createdAt
) {}
