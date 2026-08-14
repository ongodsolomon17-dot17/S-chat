package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        UUID senderId,
        UUID receiverId,
        String content,
        String attachmentUrl,
        Instant sentAt,
        Instant readAt
) {}
