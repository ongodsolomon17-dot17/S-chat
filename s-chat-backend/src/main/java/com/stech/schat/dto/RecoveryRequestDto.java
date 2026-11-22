package com.stech.schat.dto;

import com.stech.schat.model.RecoveryChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecoveryRequestDto(
        @NotBlank(message = "Enter your username or email") @Size(max = 255) String identifier,
        @NotNull(message = "Choose how to receive your code") RecoveryChannel channel
) {}
