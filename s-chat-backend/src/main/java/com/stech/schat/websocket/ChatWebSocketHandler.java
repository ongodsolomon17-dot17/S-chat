package com.stech.schat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight JSON-over-WebSocket protocol (no STOMP broker needed for 1:1 chat).
 *
 * Client -> server frame:
 *   { "type": "chat", "to": "<receiver-uuid>", "content": "...", "attachmentUrl": "..." }
 *
 * Server -> client frames:
 *   { "type": "chat", ...ChatMessageDto }         — a new message for you
 *   { "type": "ack", "id": "<message-uuid>" }     — your message was persisted
 *   { "type": "error", "message": "..." }
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    // One active session per online user. A user with multiple tabs only keeps the latest —
    // fine for phase 2; multi-session fan-out can be added later without changing the protocol.
    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = currentUserId(session);
        sessions.put(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(currentUserId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID senderId = currentUserId(session);

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            sendError(session, "Malformed message");
            return;
        }

        if (!"chat".equals(payload.get("type"))) {
            sendError(session, "Unknown message type");
            return;
        }

        try {
            UUID receiverId = UUID.fromString(String.valueOf(payload.get("to")));
            String content = String.valueOf(payload.getOrDefault("content", ""));
            String attachmentUrl = payload.get("attachmentUrl") != null ? String.valueOf(payload.get("attachmentUrl")) : null;

            if (content.length() > 5000) {
                sendError(session, "Message is too long");
                return;
            }
            if (attachmentUrl != null && attachmentUrl.length() > 2048) {
                sendError(session, "Attachment URL is too long");
                return;
            }

            if (content.isBlank() && attachmentUrl == null) {
                sendError(session, "Message cannot be empty");
                return;
            }

            ChatMessageDto saved = chatService.sendMessage(senderId, receiverId, content, attachmentUrl);

            sendJson(session, Map.of("type", "ack", "id", saved.id().toString()));

            WebSocketSession recipientSession = sessions.get(receiverId);
            if (recipientSession != null && recipientSession.isOpen()) {
                chatService.markDelivered(saved.id());
                sendJson(recipientSession, Map.of(
                        "type", "chat",
                        "id", saved.id().toString(),
                        "senderId", saved.senderId().toString(),
                        "receiverId", saved.receiverId().toString(),
                        "content", saved.content(),
                        "attachmentUrl", saved.attachmentUrl() == null ? "" : saved.attachmentUrl(),
                        "sentAt", saved.sentAt().toString()
                ));
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Invalid recipient");
        } catch (Exception e) {
            sendError(session, e.getMessage() != null ? e.getMessage() : "Could not send message");
        }
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendJson(session, Map.of("type", "error", "message", message));
    }

    private void sendJson(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private UUID currentUserId(WebSocketSession session) {
        return UUID.fromString((String) session.getAttributes().get("userId"));
    }
}
