package com.stech.schat.controller;

import com.stech.schat.dto.FriendRequestDto;
import com.stech.schat.dto.FriendProfileDto;
import com.stech.schat.dto.SendFriendRequestRequest;
import com.stech.schat.service.FriendService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.stech.schat.controller.UserController.currentUserId;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/request")
    public ResponseEntity<FriendRequestDto> sendRequest(Authentication auth, @Valid @RequestBody SendFriendRequestRequest request) {
        return ResponseEntity.ok(friendService.sendRequest(currentUserId(auth), request));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendRequestDto> accept(Authentication auth, @PathVariable UUID requestId) {
        return ResponseEntity.ok(friendService.respond(currentUserId(auth), requestId, true));
    }

    @PostMapping("/requests/{requestId}/decline")
    public ResponseEntity<FriendRequestDto> decline(Authentication auth, @PathVariable UUID requestId) {
        return ResponseEntity.ok(friendService.respond(currentUserId(auth), requestId, false));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<FriendRequestDto>> incoming(Authentication auth) {
        return ResponseEntity.ok(friendService.listPendingIncoming(currentUserId(auth)));
    }

    @GetMapping
    public ResponseEntity<List<FriendRequestDto>> myFriends(Authentication auth) {
        return ResponseEntity.ok(friendService.listFriends(currentUserId(auth)));
    }

    @GetMapping("/{friendId}/profile")
    public ResponseEntity<FriendProfileDto> friendProfile(Authentication auth, @PathVariable UUID friendId) {
        return ResponseEntity.ok(friendService.getFriendProfile(currentUserId(auth), friendId));
    }

    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> removeFriend(Authentication auth, @PathVariable UUID friendId) {
        friendService.removeFriend(currentUserId(auth), friendId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{friendId}/block")
    public ResponseEntity<Void> block(Authentication auth, @PathVariable UUID friendId) {
        friendService.block(currentUserId(auth), friendId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{friendId}/block")
    public ResponseEntity<Void> unblock(Authentication auth, @PathVariable UUID friendId) {
        friendService.unblock(currentUserId(auth), friendId);
        return ResponseEntity.noContent().build();
    }
}
