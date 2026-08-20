package com.stech.schat.controller;

import com.stech.schat.dto.AiChatResponse;
import com.stech.schat.dto.AiChatRequest;
import com.stech.schat.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) { this.geminiService = geminiService; }

    @GetMapping("/history")
    public ResponseEntity<List<com.stech.schat.dto.AiChatMessage>> history(Authentication auth) {
        return ResponseEntity.ok(geminiService.history(currentUserId(auth)));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(Authentication auth, @RequestBody AiChatRequest request) {
        return ResponseEntity.ok(geminiService.generate(currentUserId(auth), request));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(Authentication auth) {
        geminiService.clearHistory(currentUserId(auth));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication auth) {
        try { return UUID.fromString(auth.getName()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid authenticated user"); }
    }
}
