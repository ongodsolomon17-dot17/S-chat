package com.stech.schat.dto;

import com.stech.schat.model.UserReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReportRequest(
        @NotNull(message = "Choose who you are reporting") UUID reportedUserId,
        UUID messageId,
        @NotNull(message = "Choose a reason") UserReport.Reason reason,
        @Size(max = 1000, message = "Details can be at most 1000 characters") String details
) {}
