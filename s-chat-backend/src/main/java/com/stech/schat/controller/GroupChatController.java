package com.stech.schat.controller;

import com.stech.schat.dto.*;
import com.stech.schat.service.GroupChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/chat/groups")
public class GroupChatController {
    private final GroupChatService groupChatService;

    public GroupChatController(GroupChatService groupChatService) {
        this.groupChatService = groupChatService;
    }

    @GetMapping
    public ResponseEntity<List<GroupSummaryDto>> list(Authentication auth) {
        return ResponseEntity.ok(groupChatService.list(currentUserId(auth)));
    }

    @PostMapping
    public ResponseEntity<GroupDetailsDto> create(Authentication auth, @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.ok(groupChatService.create(currentUserId(auth), request));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailsDto> details(Authentication auth, @PathVariable UUID groupId) {
        return ResponseEntity.ok(groupChatService.details(currentUserId(auth), groupId));
    }

    @GetMapping("/{groupId}/messages")
    public ResponseEntity<List<GroupMessageDto>> history(Authentication auth,
                                                          @PathVariable UUID groupId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(groupChatService.history(currentUserId(auth), groupId, page, size));
    }

    @PostMapping("/{groupId}/messages")
    public ResponseEntity<GroupMessageDto> send(Authentication auth,
                                                @PathVariable UUID groupId,
                                                @Valid @RequestBody SendGroupMessageRequest request) {
        return ResponseEntity.ok(groupChatService.sendMessage(currentUserId(auth), groupId,
                request.content(), request.attachmentUrl()));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(Authentication auth,
                                          @PathVariable UUID groupId,
                                          @Valid @RequestBody AddGroupMemberRequest request) {
        groupChatService.addMember(currentUserId(auth), groupId, request.userId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(Authentication auth,
                                             @PathVariable UUID groupId,
                                             @PathVariable UUID userId) {
        groupChatService.removeMember(currentUserId(auth), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/members/{userId}/admin")
    public ResponseEntity<Void> promote(Authentication auth,
                                        @PathVariable UUID groupId,
                                        @PathVariable UUID userId) {
        groupChatService.promote(currentUserId(auth), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/members/{userId}/admin")
    public ResponseEntity<Void> demote(Authentication auth,
                                       @PathVariable UUID groupId,
                                       @PathVariable UUID userId) {
        groupChatService.demote(currentUserId(auth), groupId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{groupId}/me")
    public ResponseEntity<Void> leave(Authentication auth, @PathVariable UUID groupId) {
        groupChatService.leave(currentUserId(auth), groupId);
        return ResponseEntity.noContent().build();
    }
}
