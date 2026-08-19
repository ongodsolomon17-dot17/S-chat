package com.stech.schat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the one active WebSocket session per online user and lets any part of the
 * backend (not just ChatWebSocketHandler) push a realtime frame to a user.
 *
 * Pulled out of ChatWebSocketHandler so REST-triggered realtime events (reactions,
 * deletions, status replies) can reuse the exact same "push if online" delivery path
 * as the WebSocket chat flow, without ChatService/ChatController depending on the
 * WebSocket handler itself (that would be a circular dependency, since the handler
 * already depends on ChatService).
 */
@Component
public class WebSocketSessionRegistry {

    private final ObjectMapper objectMapper;
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(UUID userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void unregister(UUID userId) {
        sessions.remove(userId);
    }

    public boolean isOnline(UUID userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public WebSocketSession sessionFor(UUID userId) {
        return sessions.get(userId);
    }

    /** Best-effort push. Silently no-ops if the user isn't connected or the send fails. */
    public void send(UUID userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
            // Best-effort realtime push — the REST/DB write already succeeded, so a
            // failed live-update just means the recipient sees it on next refresh.
        }
    }

    public void sendError(WebSocketSession session, String message) {
        send(session, Map.of("type", "error", "message", message));
    }

    private void send(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception ignored) {
        }
    }
}
