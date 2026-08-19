package com.stech.schat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessageDto(
        UUID id,
        UUID senderId,
        UUID receiverId,
        String content,
        String attachmentUrl,
        Instant sentAt,
        Instant deliveredAt,
        Instant readAt,
        ReplyPreviewDto replyTo,
        UUID replyToStatusId,
        List<ReactionSummaryDto> reactions,
        boolean deleted
) {}
