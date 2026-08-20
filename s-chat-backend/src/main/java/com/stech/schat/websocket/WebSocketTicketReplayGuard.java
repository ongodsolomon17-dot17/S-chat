package com.stech.schat.websocket;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time replay guard for short-lived WebSocket tickets.
 *
 * Tickets are deliberately tiny (30 seconds). The in-memory guard prevents a captured
 * ticket from opening a second socket on the same backend instance. If the service is
 * scaled horizontally, replace this with a shared Redis/DB nonce store.
 */
@Component
public class WebSocketTicketReplayGuard {
    private final Map<String, Long> used = new ConcurrentHashMap<>();

    public boolean consume(String jti, long expiresAtEpochSeconds) {
        if (jti == null || jti.isBlank()) return false;
        long now = Instant.now().getEpochSecond();
        used.entrySet().removeIf(e -> e.getValue() <= now);
        return used.putIfAbsent(jti, expiresAtEpochSeconds) == null;
    }
}
