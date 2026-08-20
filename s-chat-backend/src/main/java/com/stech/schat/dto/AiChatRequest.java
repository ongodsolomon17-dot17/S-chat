package com.stech.schat.dto;

import java.util.List;

public record AiChatRequest(
        List<AiChatMessage> messages,
        String clientMessageId
) {}
