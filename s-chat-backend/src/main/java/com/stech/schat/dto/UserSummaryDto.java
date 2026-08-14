package com.stech.schat.dto;

import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String username,
        String publicId,
        String profilePictureUrl,
        boolean deleted
) {}
