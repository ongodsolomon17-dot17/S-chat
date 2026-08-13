package com.stech.schat.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role,
        long expiresInSeconds
) {}
