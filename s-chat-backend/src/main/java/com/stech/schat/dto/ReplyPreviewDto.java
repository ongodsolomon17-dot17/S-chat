package com.stech.schat.dto;

import java.util.UUID;

/**
 * Compact preview of the message a reply points to — never the full original message,
 * per the "avoid recursive data explosions" requirement. A reply-to-a-reply only ever
 * carries one level of preview (the immediate parent's snippet), so chains never nest.
 *
 * attachmentUrl is included (not just a hasAttachment flag) so the client can tell a
 * photo/video/voice-note reply apart and show the right icon — it's a single URL string,
 * not the original message, so it doesn't reintroduce the "embeds the whole message" problem.
 */
public record ReplyPreviewDto(
        UUID messageId,
        UUID senderId,
        String snippet,
        String attachmentUrl,
        boolean deleted
) {}
