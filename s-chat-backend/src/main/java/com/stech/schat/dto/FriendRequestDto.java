package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestDto(
        UUID requestId,
        UserSummaryDto otherUser,
        String status,
        boolean incoming, // true if the current user is the addressee
        Instant createdAt
) {}
