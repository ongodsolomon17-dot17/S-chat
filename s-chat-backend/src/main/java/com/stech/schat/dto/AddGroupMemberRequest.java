package com.stech.schat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddGroupMemberRequest(@NotNull UUID userId) {}
