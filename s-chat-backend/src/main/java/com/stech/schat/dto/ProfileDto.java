package com.stech.schat.dto;

import java.util.UUID;

public record ProfileDto(
        UUID id,
        String username,
        String email,
        String phoneNumber,
        String publicId,
        String profilePictureUrl,
        boolean addByIdOnly,
        boolean approvalRequired,
        String role
) {}
