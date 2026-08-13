package com.stech.schat.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        List<String> messages
) {
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, List.of(message));
    }

    public static ApiError of(int status, String error, List<String> messages) {
        return new ApiError(Instant.now(), status, error, messages);
    }
}
