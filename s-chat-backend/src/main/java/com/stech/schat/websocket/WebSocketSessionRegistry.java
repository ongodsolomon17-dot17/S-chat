package com.stech.schat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every active WebSocket session per online user (several tabs/devices are allowed) and lets any
 * part of the backend push a realtime frame to a user.
 *
 * Why sessions are wrapped: a raw {@link WebSocketSession} must not be written to from two threads at the
 * same time. Chat pushes come from the sender's socket thread, REST calls (reactions, status replies) and
 * scheduled work, so two messages arriving at the same moment could collide, throw, and — because pushes are
 * best-effort — silently vanish. {@link ConcurrentWebSocketSessionDecorator} serialises the writes and also
 * drops a client that stops reading instead of letting it block everyone else.
 */
@Component
public class WebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRegistry.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;
    /** Upper bound of simultaneous sockets for one account; the oldest is closed beyond this. */
    private static final int MAX_SESSIONS_PER_USER = 5;

    private final ObjectMapper objectMapper;
    // userId -> (raw session id -> thread-safe wrapper)
    private final Map<UUID, Map<String, ConcurrentWebSocketSessionDecorator>> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(UUID userId, WebSocketSession session) {
        Map<String, ConcurrentWebSocketSessionDecorator> mine =
                sessions.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        mine.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES));
        if (mine.size() > MAX_SESSIONS_PER_USER) {
            mine.values().stream().filter(s -> !s.getId().equals(session.getId())).findFirst().ifPresent(old -> {
                mine.remove(old.getId());
                closeQuietly(old, CloseStatus.POLICY_VIOLATION.withReason("Too many connections"));
            });
        }
    }

    /** Remove only this session, so the user's other tabs/devices stay connected. */
    public void unregister(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessions.computeIfPresent(userId, (id, mine) -> {
            mine.remove(session.getId());
            return mine.isEmpty() ? null : mine;
        });
    }

    /** Closes every socket of a user (account suspended/deleted, password changed...). */
    public void disconnect(UUID userId, String reason) {
        Map<String, ConcurrentWebSocketSessionDecorator> mine = sessions.remove(userId);
        if (mine == null) return;
        mine.values().forEach(s -> closeQuietly(s, CloseStatus.POLICY_VIOLATION.withReason(reason)));
    }

    public boolean isOnline(UUID userId) {
        Map<String, ConcurrentWebSocketSessionDecorator> mine = sessions.get(userId);
        return mine != null && mine.values().stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Best-effort push to every open socket of the user. Never throws. */
    public void send(UUID userId, Object payload) {
        Map<String, ConcurrentWebSocketSessionDecorator> mine = sessions.get(userId);
        if (mine == null || mine.isEmpty()) return;
        TextMessage message = toMessage(payload);
        if (message == null) return;
        mine.values().forEach(s -> sendTo(s, message));
    }

    /** Reply on one specific socket (e.g. an ack or error), using its thread-safe wrapper if registered. */
    public void sendToSession(WebSocketSession session, Object payload) {
        if (session == null) return;
        TextMessage message = toMessage(payload);
        if (message == null) return;
        Map<String, ConcurrentWebSocketSessionDecorator> mine = userSessions(session);
        WebSocketSession target = mine != null && mine.containsKey(session.getId()) ? mine.get(session.getId()) : session;
        sendTo(target, message);
    }

    public void sendError(WebSocketSession session, String message) {
        sendToSession(session, Map.of("type", "error", "message", message));
    }

    // ---- internals ----

    private Map<String, ConcurrentWebSocketSessionDecorator> userSessions(WebSocketSession session) {
        Object uid = session.getAttributes().get("userId");
        if (uid == null) return null;
        try {
            return sessions.get(UUID.fromString(uid.toString()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TextMessage toMessage(Object payload) {
        try {
            return new TextMessage(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Could not serialise websocket frame: {}", e.toString());
            return null;
        }
    }

    private void sendTo(WebSocketSession session, TextMessage message) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(message);
        } catch (Exception e) {
            // Best-effort realtime push — the DB write already succeeded, so the recipient still sees the
            // message on their next load. A slow/broken socket is closed so it can't pile up memory.
            log.debug("WebSocket send failed for session {}: {}", session.getId(), e.toString());
            closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // already closed
        }
    }
}
