package com.stech.schat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stech.schat.dto.CallRecordDto;
import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.model.CallRecord;
import com.stech.schat.service.CallService;
import com.stech.schat.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatService chatService;
    private final CallService callService;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;

    public ChatWebSocketHandler(ChatService chatService, CallService callService,
                                ObjectMapper objectMapper, WebSocketSessionRegistry sessionRegistry) {
        this.chatService = chatService;
        this.callService = callService;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(currentUserId(session), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessionRegistry.unregister(currentUserId(session));
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

        try {
            String type = String.valueOf(payload.get("type"));
            switch (type) {
                case "chat" -> handleChat(session, senderId, payload);
                case "call_invite" -> handleCallInvite(session, senderId, payload);
                case "call_accept" -> handleCallAccept(senderId, payload);
                case "call_reject" -> handleCallReject(senderId, payload);
                case "call_end" -> handleCallEnd(senderId, payload);
                case "webrtc_offer", "webrtc_answer", "ice_candidate" ->
                        relayCallSignal(senderId, payload, type);
                default -> sendError(session, "Unknown message type");
            }
        } catch (IllegalArgumentException e) {
            sendError(session, "Invalid request");
        } catch (Exception e) {
            sendError(session, e.getMessage() != null ? e.getMessage() : "Could not process request");
        }
    }

    private void handleChat(WebSocketSession session, UUID senderId, Map<String, Object> p) throws IOException {
        UUID receiverId = UUID.fromString(String.valueOf(p.get("to")));
        String content = String.valueOf(p.getOrDefault("content", ""));
        String attachmentUrl = p.get("attachmentUrl") == null ? null : String.valueOf(p.get("attachmentUrl"));
        UUID replyTo = p.get("replyToMessageId") != null && !String.valueOf(p.get("replyToMessageId")).isBlank()
                ? UUID.fromString(String.valueOf(p.get("replyToMessageId"))) : null;

        if (content.length() > 5000) { sendError(session, "Message is too long"); return; }
        if (attachmentUrl != null && attachmentUrl.length() > 2048) { sendError(session, "Attachment URL is too long"); return; }
        if (content.isBlank() && attachmentUrl == null) { sendError(session, "Message cannot be empty"); return; }

        ChatMessageDto saved = chatService.sendMessage(senderId, receiverId, content, attachmentUrl, replyTo, null);
        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "ack");
        ack.put("id", saved.id().toString());
        if (p.get("clientMessageId") != null) {
            ack.put("clientMessageId", String.valueOf(p.get("clientMessageId")));
        }
        sendJson(session, ack);
        chatService.pushToOtherParticipant(saved, senderId);
    }

    private void handleCallInvite(WebSocketSession session, UUID callerId, Map<String, Object> p) throws IOException {
        UUID calleeId = UUID.fromString(String.valueOf(p.get("to")));
        if (!sessionRegistry.isOnline(calleeId)) { sendError(session, "That user is currently offline."); return; }

        CallRecord.CallType type;
        try { type = CallRecord.CallType.valueOf(String.valueOf(p.get("callType")).toUpperCase()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid call type"); }

        CallRecordDto call = callService.start(callerId, calleeId, type);
        // Acknowledge the caller first so the client has the callId before the recipient
        // can possibly accept the call. This avoids a fast-accept race.
        sendJson(session, Map.of("type", "call_started", "callId", call.id().toString(),
                "to", calleeId.toString(), "callType", call.callType().name()));

        Map<String, Object> incoming = new HashMap<>();
        incoming.put("type", "call_invite");
        incoming.put("callId", call.id().toString());
        incoming.put("from", callerId.toString());
        incoming.put("fromName", String.valueOf(p.getOrDefault("fromName", "Friend")));
        incoming.put("callType", call.callType().name());
        sessionRegistry.send(calleeId, incoming);
    }

    private void handleCallAccept(UUID userId, Map<String, Object> p) throws IOException {
        UUID callId = UUID.fromString(String.valueOf(p.get("callId")));
        CallRecordDto call = callService.accept(userId, callId);
        UUID other = call.callerId().equals(userId) ? call.calleeId() : call.callerId();
        sessionRegistry.send(other, Map.of("type", "call_accept", "callId", call.id().toString()));
    }

    private void handleCallReject(UUID userId, Map<String, Object> p) throws IOException {
        UUID callId = UUID.fromString(String.valueOf(p.get("callId")));
        CallRecordDto call = callService.reject(userId, callId);
        UUID other = call.callerId().equals(userId) ? call.calleeId() : call.callerId();
        sessionRegistry.send(other, Map.of("type", "call_reject", "callId", call.id().toString()));
    }

    private void handleCallEnd(UUID userId, Map<String, Object> p) throws IOException {
        UUID callId = UUID.fromString(String.valueOf(p.get("callId")));
        CallRecordDto call = callService.end(userId, callId);
        UUID other = call.callerId().equals(userId) ? call.calleeId() : call.callerId();
        sessionRegistry.send(other, Map.of("type", "call_end", "callId", call.id().toString(),
                "status", call.status().name()));
    }

    private void relayCallSignal(UUID userId, Map<String, Object> p, String type) {
        UUID callId = UUID.fromString(String.valueOf(p.get("callId")));
        CallRecordDto call = callService.requireParticipantForSignal(userId, callId);
        UUID other = call.callerId().equals(userId) ? call.calleeId() : call.callerId();
        Map<String, Object> frame = new HashMap<>(p);
        frame.put("type", type);
        frame.put("from", userId.toString());
        sessionRegistry.send(other, frame);
    }

    private void sendError(WebSocketSession session, String message) throws IOException {
        sendJson(session, Map.of("type", "error", "message", message));
    }

    private void sendJson(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private UUID currentUserId(WebSocketSession session) {
        return UUID.fromString((String) session.getAttributes().get("userId"));
    }
}
