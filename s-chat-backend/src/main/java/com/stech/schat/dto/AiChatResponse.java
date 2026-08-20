package com.stech.schat.dto;

import java.util.List;

public record AiChatResponse(
        String text,
        List<AiChatMessage> history
) {}
