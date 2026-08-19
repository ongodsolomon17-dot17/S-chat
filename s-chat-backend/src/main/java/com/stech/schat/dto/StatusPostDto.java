package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record StatusPostDto(
        UUID id,
        UserSummaryDto author,
        String mediaUrl,
        String caption,
        String textContent,
        String backgroundColor,
        Instant createdAt,
        Instant expiresAt
) {}
