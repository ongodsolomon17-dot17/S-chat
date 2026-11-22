package com.stech.schat.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveReportRequest(@NotNull(message = "Choose RESOLVED or DISMISSED") String action) {}
