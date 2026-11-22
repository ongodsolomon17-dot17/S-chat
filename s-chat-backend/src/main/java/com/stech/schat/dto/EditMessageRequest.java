package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 5000, message = "Message is too long")
        String content
) {}
