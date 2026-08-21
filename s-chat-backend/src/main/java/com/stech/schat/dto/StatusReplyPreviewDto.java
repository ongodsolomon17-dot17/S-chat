package com.stech.schat.dto;

import java.util.UUID;

/** Compact snapshot of the status a chat message was sent in reply to. */
public record StatusReplyPreviewDto(
        UUID statusId,
        UUID authorId,
        String authorName,
        String mediaUrl,
        String textContent,
        String caption,
        String backgroundColor
) {}
