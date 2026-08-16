package com.stech.schat.controller;

import com.stech.schat.dto.AiChatRequest;
import com.stech.schat.dto.AiChatResponse;
import com.stech.schat.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(Authentication auth, @RequestBody AiChatRequest request) {
        // Authentication is deliberately required by SecurityConfig. The service does not
        // need the user id yet because AI history is kept client-side for this phase.
        return ResponseEntity.ok(geminiService.generate(request));
    }
}
