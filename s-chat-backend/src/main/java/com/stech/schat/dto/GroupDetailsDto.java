package com.stech.schat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupDetailsDto(
        UUID id,
        String name,
        String avatarUrl,
        UUID createdBy,
        Instant createdAt,
        String myRole,
        List<GroupMemberDto> members
) {}
