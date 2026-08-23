package com.stech.schat.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupMessageDto(
        UUID id,
        UUID groupId,
        UUID senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        String attachmentUrl,
        Instant sentAt,
        boolean deleted
) {}
