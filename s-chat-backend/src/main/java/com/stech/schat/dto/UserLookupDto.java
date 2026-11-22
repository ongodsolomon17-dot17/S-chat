package com.stech.schat.dto;

import java.util.UUID;

/** What a signed-in visitor sees at /chat/{username}, including how they relate to this user. */
public record UserLookupDto(
        UUID id,
        String username,
        String publicId,
        String profilePictureUrl,
        String bio,
        String relationship,   // SELF, FRIENDS, REQUEST_SENT, REQUEST_RECEIVED, NONE
        UUID pendingRequestId  // set when relationship is REQUEST_RECEIVED
) {}
