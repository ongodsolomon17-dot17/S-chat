package com.stech.schat.controller;

import com.stech.schat.dto.AiChatResponse;
import com.stech.schat.dto.AiChatRequest;
import com.stech.schat.service.GeminiService;
import com.stech.schat.service.RateLimiter; // <-- update to your actual package/class
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final GeminiService geminiService;
    private final RateLimiter rateLimiter;

    public AiController(GeminiService geminiService, RateLimiter rateLimiter) {
        this.geminiService = geminiService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/history")
    public ResponseEntity<List<com.stech.schat.dto.AiChatMessage>> history(Authentication auth) {
        return ResponseEntity.ok(geminiService.history(currentUserId(auth)));
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(Authentication auth, @RequestBody AiChatRequest request) {
        UUID userId = currentUserId(auth);
        if (!rateLimiter.tryConsume(userId)) {
            throw new com.stech.schat.exception.RateLimitExceededException("You're sending messages to STech AI too quickly. Please wait a moment.");
        }
        return ResponseEntity.ok(geminiService.generate(userId, request));
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