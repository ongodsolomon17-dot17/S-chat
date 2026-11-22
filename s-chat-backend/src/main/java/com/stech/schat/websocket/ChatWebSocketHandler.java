package com.stech.schat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stech.schat.dto.CallRecordDto;
import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.model.CallRecord;
import com.stech.schat.service.CallService;
import com.stech.schat.service.ChatService;
import com.stech.schat.service.FriendService;
import com.stech.schat.service.GroupChatService;
import com.stech.schat.dto.GroupMessageDto;
import com.stech.schat.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.time.Instant;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatService chatService;
    private final CallService callService;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionRegistry sessionRegistry;
    private final UserRepository userRepository;
    private final GroupChatService groupChatService;
    private final FriendService friendService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int MAX_FRAME_CHARS = 64 * 1024;
    private static final int MAX_MESSAGES_PER_10S = 80;
    private final ConcurrentMap<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatService chatService, CallService callService,
                                ObjectMapper objectMapper, WebSocketSessionRegistry sessionRegistry, UserRepository userRepository,
                                GroupChatService groupChatService,
                                FriendService friendService) {
        this.chatService = chatService;
        this.callService = callService;
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
        this.groupChatService = groupChatService;
        this.friendService = friendService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = currentUserId(session);
        sessionRegistry.register(userId, session);
        // Everything sent while this user was offline is now delivered; tell the senders.
        try {
            chatService.markPendingDeliveredOnConnect(userId);
        } catch (Exception e) {
            log.warn("Could not mark pending messages delivered for {}: {}", userId, e.toString());
        }
        announcePresence(userId, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = currentUserId(session);
        sessionRegistry.unregister(userId, session);
        rateWindows.remove(session.getId());
        // A newer session for the same user may already have replaced this one.
        if (!sessionRegistry.isOnline(userId)) {
            announcePresence(userId, false);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID senderId = currentUserId(session);
        if (message.getPayloadLength() > MAX_FRAME_CHARS) {
            sendError(session, "Message is too large");
            session.close(new CloseStatus(1009, "Message too large"));
            return;
        }
        if (!allowMessage(session)) {
            sendError(session, "Too many requests. Please slow down.");
            return;
        }
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
                case "group_chat" -> handleGroupChat(session, senderId, payload);
                case "typing" -> handleTyping(senderId, payload);
                case "ping" -> sendJson(session, Map.of("type", "pong"));
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
            if (com.stech.schat.exception.ReadOnlyModeDetector.isReadOnlyViolation(e)) {
                sendError(session, com.stech.schat.exception.ReadOnlyModeDetector.USER_MESSAGE);
            } else {
                sendError(session, e.getMessage() != null ? e.getMessage() : "Could not process request");
            }
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

    /** Relays "X is typing" to the other participant only. Never persisted. */
    private void handleTyping(UUID senderId, Map<String, Object> p) {
        UUID to = UUID.fromString(String.valueOf(p.get("to")));
        if (!sessionRegistry.isOnline(to) || !friendService.areFriends(senderId, to)) return;
        boolean typing = Boolean.parseBoolean(String.valueOf(p.get("isTyping")));
        sessionRegistry.send(to, Map.of("type", "typing", "from", senderId.toString(), "isTyping", typing));
    }

    /** Tells a user's friends they came online/offline; on connect also sends who is online right now. */
    private void announcePresence(UUID userId, boolean online) {
        try {
            List<UUID> friendIds = friendService.listFriends(userId).stream()
                    .map(f -> f.otherUser().id())
                    .toList();
            for (UUID friendId : friendIds) {
                sessionRegistry.send(friendId, Map.of("type", "presence", "userId", userId.toString(), "online", online));
            }
            if (online) {
                List<String> onlineFriends = friendIds.stream()
                        .filter(sessionRegistry::isOnline)
                        .map(UUID::toString)
                        .toList();
                sessionRegistry.send(userId, Map.of("type", "presence_snapshot", "online", onlineFriends));
            }
        } catch (Exception e) {
            log.warn("Presence broadcast failed for {}: {}", userId, e.toString());
        }
    }

    private void handleGroupChat(WebSocketSession session, UUID senderId, Map<String, Object> p) throws IOException {
        UUID groupId = UUID.fromString(String.valueOf(p.get("groupId")));
        String content = String.valueOf(p.getOrDefault("content", ""));
        String attachmentUrl = p.get("attachmentUrl") == null ? null : String.valueOf(p.get("attachmentUrl"));
        if (content.length() > 5000) { sendError(session, "Message is too long"); return; }
        if (attachmentUrl != null && attachmentUrl.length() > 2048) { sendError(session, "Attachment URL is too long"); return; }
        if (content.isBlank() && attachmentUrl == null) { sendError(session, "Message cannot be empty"); return; }

        GroupMessageDto saved = groupChatService.sendMessage(senderId, groupId, content, attachmentUrl);
        Map<String, Object> ack = new HashMap<>();
        ack.put("type", "group_ack");
        ack.put("id", saved.id().toString());
        ack.put("groupId", groupId.toString());
        if (p.get("clientMessageId") != null) ack.put("clientMessageId", String.valueOf(p.get("clientMessageId")));
        sendJson(session, ack);
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
        String callerName = userRepository.findById(callerId).map(u -> u.getUsername()).orElse("Friend");
        incoming.put("fromName", callerName);
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
        if (call.status() != CallRecord.CallStatus.ACCEPTED) {
            throw new IllegalArgumentException("Call is not connected yet");
        }
        UUID other = call.callerId().equals(userId) ? call.calleeId() : call.callerId();
        Map<String, Object> frame = new HashMap<>(p);
        frame.put("type", type);
        frame.put("from", userId.toString());
        sessionRegistry.send(other, frame);
    }

    private boolean allowMessage(WebSocketSession session) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> window = rateWindows.computeIfAbsent(session.getId(), k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > 10_000) window.removeFirst();
            if (window.size() >= MAX_MESSAGES_PER_10S) return false;
            window.addLast(now);
            return true;
        }
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
