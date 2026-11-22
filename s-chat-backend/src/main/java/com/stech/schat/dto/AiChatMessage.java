package com.stech.schat.dto;

import java.time.Instant;

/** One turn of the AI conversation. {@code createdAt} is set on history responses and ignored on requests. */
public record AiChatMessage(
        String role,
        String content,
        Instant createdAt
) {
    public AiChatMessage(String role, String content) {
        this(role, content, null);
    }
}
