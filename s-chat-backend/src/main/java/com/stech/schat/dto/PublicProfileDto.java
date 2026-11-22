package com.stech.schat.dto;

import java.time.Instant;

/** What an anonymous visitor may see at /chat/{username}. Deliberately minimal: no email, phone, or internal IDs. */
public record PublicProfileDto(String username, String profilePictureUrl, String bio, Instant memberSince) {}
