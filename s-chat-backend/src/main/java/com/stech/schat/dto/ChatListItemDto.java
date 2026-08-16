package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatListItemDto(
        UserSummaryDto otherUser,
        String latestMessage,
        String latestAttachmentUrl,
        Instant latestMessageAt,
        UUID latestSenderId
) {}
