package com.stech.schat.controller;

import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.dto.StatusPostDto;
import com.stech.schat.dto.StatusReplyRequest;
import com.stech.schat.service.StatusService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final StatusService statusService;
    private final com.stech.schat.security.UserActionRateLimiter actionLimiter;

    public StatusController(StatusService statusService, com.stech.schat.security.UserActionRateLimiter actionLimiter) {
        this.statusService = statusService;
        this.actionLimiter = actionLimiter;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<StatusPostDto> create(
            Authentication auth,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "textContent", required = false) String textContent,
            @RequestParam(value = "backgroundColor", required = false) String backgroundColor
    ) throws Exception {
        UUID userId = currentUserId(auth);
        if (!actionLimiter.tryConsume("status-post", userId, 10)) {
            throw new com.stech.schat.exception.RateLimitExceededException("You're posting too quickly. Please wait a moment.");
        }
        return ResponseEntity.ok(statusService.create(userId, file, caption, textContent, backgroundColor));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<StatusPostDto>> feed(Authentication auth) {
        return ResponseEntity.ok(statusService.friendsFeed(currentUserId(auth)));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<StatusPostDto>> mine(Authentication auth) {
        return ResponseEntity.ok(statusService.myStatuses(currentUserId(auth)));
    }

    @DeleteMapping("/{statusId}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID statusId) {
        statusService.delete(currentUserId(auth), statusId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{statusId}/reply")
    public ResponseEntity<ChatMessageDto> reply(
            Authentication auth,
            @PathVariable UUID statusId,
            @Valid @RequestBody StatusReplyRequest request
    ) {
        return ResponseEntity.ok(statusService.reply(currentUserId(auth), statusId, request.content()));
    }
}
