package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RecoveryResetRequest(
        @NotBlank(message = "Enter your username or email") @Size(max = 255) String identifier,

        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "^\\d{6}$", message = "The code is 6 digits")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must include an uppercase letter, a lowercase letter, and a number")
        String newPassword
) {}
