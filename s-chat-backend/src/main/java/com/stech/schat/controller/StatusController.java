package com.stech.schat.controller;

import com.stech.schat.dto.StatusPostDto;
import com.stech.schat.service.StatusService;
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

    public StatusController(StatusService statusService) {
        this.statusService = statusService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<StatusPostDto> create(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption
    ) throws Exception {
        return ResponseEntity.ok(statusService.create(currentUserId(auth), file, caption));
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
}
