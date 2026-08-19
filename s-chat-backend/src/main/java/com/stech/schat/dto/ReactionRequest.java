package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionRequest(
        // 32 rather than 8: the "+" full-emoji-picker (Feature: broader reactions) can send
        // multi-codepoint sequences (skin-tone modifiers, ZWJ family/profession emoji, flags),
        // which run well past a single emoji's simple visual width in UTF-16 code units.
        @NotBlank @Size(max = 32) String reactionType
) {}
