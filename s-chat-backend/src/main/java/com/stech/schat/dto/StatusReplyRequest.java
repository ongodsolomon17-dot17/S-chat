package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusReplyRequest(
        @NotBlank @Size(max = 5000) String content
) {}
