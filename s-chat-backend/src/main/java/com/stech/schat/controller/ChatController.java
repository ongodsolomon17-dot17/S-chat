package com.stech.schat.controller;

import com.stech.schat.dto.ChatListItemDto;
import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.dto.ReactionRequest;
import com.stech.schat.service.ChatService;
import com.stech.schat.service.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final StorageService storageService;

    public ChatController(ChatService chatService, StorageService storageService) {
        this.chatService = chatService;
        this.storageService = storageService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<ChatListItemDto>> chatList(Authentication auth) {
        return ResponseEntity.ok(chatService.getChatList(currentUserId(auth)));
    }

    @GetMapping("/history/{friendUserId}")
    public ResponseEntity<List<ChatMessageDto>> history(
            Authentication auth,
            @PathVariable UUID friendUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok(chatService.getConversation(currentUserId(auth), friendUserId, page, size));
    }

    @PostMapping("/read/{friendUserId}")
    public ResponseEntity<Void> markRead(Authentication auth, @PathVariable UUID friendUserId) {
        chatService.markConversationRead(currentUserId(auth), friendUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/attachment", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadAttachment(Authentication auth, @RequestParam("file") MultipartFile file) throws Exception {
        currentUserId(auth);
        String url = storageService.upload("attachments", file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<ChatMessageDto> addReaction(
            Authentication auth,
            @PathVariable UUID messageId,
            @Valid @RequestBody ReactionRequest request
    ) {
        return ResponseEntity.ok(chatService.addReaction(currentUserId(auth), messageId, request.reactionType()));
    }

    @DeleteMapping("/messages/{messageId}/reactions")
    public ResponseEntity<ChatMessageDto> removeReaction(Authentication auth, @PathVariable UUID messageId) {
        return ResponseEntity.ok(chatService.removeReaction(currentUserId(auth), messageId));
    }

    @DeleteMapping("/messages/{messageId}/mine")
    public ResponseEntity<Void> hideForMe(Authentication auth, @PathVariable UUID messageId) {
        chatService.hideForMe(currentUserId(auth), messageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDto> deleteMessage(Authentication auth, @PathVariable UUID messageId) {
        return ResponseEntity.ok(chatService.deleteMessage(currentUserId(auth), messageId));
    }
}
