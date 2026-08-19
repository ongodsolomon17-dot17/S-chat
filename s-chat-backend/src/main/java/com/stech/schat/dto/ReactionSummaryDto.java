package com.stech.schat.dto;

public record ReactionSummaryDto(
        String reactionType,
        long count,
        boolean reactedByMe
) {}
