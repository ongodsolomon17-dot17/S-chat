package com.stech.schat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 50) List<UUID> memberIds,
        @Size(max = 512) String avatarUrl
) {}
