package com.stech.schat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        String username,
        String role,
        String publicId,
        long expiresInSeconds
) {}
