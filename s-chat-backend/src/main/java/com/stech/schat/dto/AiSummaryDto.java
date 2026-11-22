package com.stech.schat.dto;

import java.time.Instant;

/** Lightweight info for the chat list: is the assistant usable, and what was the last message. */
public record AiSummaryDto(boolean available, String latestRole, String latestContent, Instant latestAt) {}
