package com.stech.schat.dto;

import jakarta.validation.constraints.Size;

public record SendGroupMessageRequest(
        @Size(max = 5000) String content,
        @Size(max = 2048) String attachmentUrl
) {}
