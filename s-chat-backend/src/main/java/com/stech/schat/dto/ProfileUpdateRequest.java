package com.stech.schat.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(

        @Pattern(regexp = "^[a-zA-Z0-9_.\\-]{3,32}$", message = "ID can only contain letters, numbers, dots, underscores and hyphens (3-32 chars)")
        String publicId,

        @Size(max = 32)
        String phoneNumber,

        Boolean addByIdOnly,

        Boolean approvalRequired
) {}
