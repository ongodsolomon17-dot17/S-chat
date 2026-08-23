package com.stech.schat.dto;

import java.util.UUID;

public record FriendProfileDto(
        UUID id,
        String username,
        String publicId,
        String profilePictureUrl
) {}
