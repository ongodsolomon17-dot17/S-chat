package com.stech.schat.controller;

import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.dto.ChatListItemDto;
import com.stech.schat.service.ChatService;
import com.stech.schat.service.StorageService;
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

    @PostMapping(value = "/attachment", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) throws Exception {
        String url = storageService.upload("attachments", file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
