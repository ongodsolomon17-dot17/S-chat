package com.stech.schat.service;

import com.stech.schat.dto.FriendRequestDto;
import com.stech.schat.dto.FriendProfileDto;
import com.stech.schat.dto.SendFriendRequestRequest;
import com.stech.schat.dto.UserSummaryDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.FriendRequest;
import com.stech.schat.model.FriendRequestStatus;
import com.stech.schat.model.User;
import com.stech.schat.repository.FriendRequestRepository;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.repository.UserBlockRepository;
import com.stech.schat.model.UserBlock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    private final com.stech.schat.websocket.WebSocketSessionRegistry sessionRegistry;

    public FriendService(FriendRequestRepository friendRequestRepository, UserRepository userRepository,
                         UserBlockRepository userBlockRepository,
                         com.stech.schat.websocket.WebSocketSessionRegistry sessionRegistry) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
        this.sessionRegistry = sessionRegistry;
    }

    // ---- real-time friend events (so nobody has to refresh to see a request or an acceptance) ----

    /** Runs after the surrounding transaction commits, so a client that reacts by reloading sees the new data. */
    private void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { action.run(); }
                    });
        } else {
            action.run();
        }
    }

    private void pushIfOnline(UUID userId, java.util.Map<String, Object> frame) {
        try {
            if (sessionRegistry.isOnline(userId)) sessionRegistry.send(userId, frame);
        } catch (Exception ignored) {
            // A missed push is harmless: clients also refresh on focus, reconnect and every minute.
        }
    }

    private void notifyNewRequest(FriendRequest fr, User requester) {
        afterCommit(() -> pushIfOnline(fr.getAddresseeId(), java.util.Map.of(
                "type", "friend_request",
                "requestId", fr.getId().toString(),
                "fromId", requester.getId().toString(),
                "fromName", requester.getUsername())));
    }

    /** Both people are now friends: tell each side about the other and show each other's online status. */
    private void notifyConnected(UUID a, UUID b) {
        afterCommit(() -> {
            String an = userRepository.findById(a).map(User::getUsername).orElse("");
            String bn = userRepository.findById(b).map(User::getUsername).orElse("");
            pushIfOnline(a, java.util.Map.of("type", "friend_accepted", "userId", b.toString(), "username", bn));
            pushIfOnline(b, java.util.Map.of("type", "friend_accepted", "userId", a.toString(), "username", an));
            if (sessionRegistry.isOnline(b)) pushIfOnline(a, java.util.Map.of("type", "presence", "userId", b.toString(), "online", true));
            if (sessionRegistry.isOnline(a)) pushIfOnline(b, java.util.Map.of("type", "presence", "userId", a.toString(), "online", true));
        });
    }

    @Transactional
    public FriendRequestDto sendRequest(UUID requesterId, SendFriendRequestRequest request) {
        User requester = getActive(requesterId);
        User target = resolveTarget(request.identifier(), request.viaId());

        if (target.getId().equals(requesterId)) {
            throw new ForbiddenActionException("You can't add yourself");
        }

        // Respect the target's own preference: if they've restricted adds to ID-only,
        // a contact-based (phone/email) match is not allowed even if it happened to match.
        if (target.isAddByIdOnly() && !request.viaId()) {
            throw new ResourceNotFoundException("No user found with that contact info");
        }

        Optional<FriendRequest> existing = friendRequestRepository
                .findByRequesterIdAndAddresseeId(requesterId, target.getId());
        if (existing.isPresent() && existing.get().getStatus() != FriendRequestStatus.DECLINED) {
            throw new ForbiddenActionException("A request already exists with this user");
        }

        // Reverse direction already pending? Auto-accept both ways instead of duplicating.
        Optional<FriendRequest> reverse = friendRequestRepository
                .findByRequesterIdAndAddresseeId(target.getId(), requesterId);
        if (reverse.isPresent() && reverse.get().getStatus() == FriendRequestStatus.PENDING) {
            FriendRequest r = reverse.get();
            r.setStatus(FriendRequestStatus.ACCEPTED);
            r.setRespondedAt(Instant.now());
            friendRequestRepository.save(r);
            notifyConnected(requesterId, target.getId());
            return toDto(r, requesterId);
        }

        FriendRequest fr = existing
                .map(e -> { e.setStatus(FriendRequestStatus.PENDING); e.setRespondedAt(null); return e; })
                .orElseGet(() -> FriendRequest.builder()
                        .requesterId(requesterId)
                        .addresseeId(target.getId())
                        .matchedVia(request.viaId() ? "ID" : "CONTACT")
                        .build());

        // Auto-accept if the target has turned approval off.
        if (!target.isApprovalRequired()) {
            fr.setStatus(FriendRequestStatus.ACCEPTED);
            fr.setRespondedAt(Instant.now());
        }

        friendRequestRepository.save(fr);
        if (fr.getStatus() == FriendRequestStatus.ACCEPTED) {
            notifyConnected(requesterId, target.getId());
        } else {
            notifyNewRequest(fr, requester);
        }
        return toDto(fr, requesterId);
    }

    @Transactional
    public FriendRequestDto respond(UUID currentUserId, UUID requestId, boolean accept) {
        FriendRequest fr = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found"));

        if (!fr.getAddresseeId().equals(currentUserId)) {
            throw new ForbiddenActionException("This request isn't addressed to you");
        }
        if (fr.getStatus() != FriendRequestStatus.PENDING) {
            throw new ForbiddenActionException("This request has already been handled");
        }

        fr.setStatus(accept ? FriendRequestStatus.ACCEPTED : FriendRequestStatus.DECLINED);
        fr.setRespondedAt(Instant.now());
        friendRequestRepository.save(fr);
        if (accept) notifyConnected(fr.getRequesterId(), fr.getAddresseeId());
        return toDto(fr, currentUserId);
    }

    public List<FriendRequestDto> listPendingIncoming(UUID currentUserId) {
        return toDtoList(friendRequestRepository.findByAddresseeIdAndStatus(currentUserId, FriendRequestStatus.PENDING), currentUserId);
    }

    public List<FriendRequestDto> listFriends(UUID currentUserId) {
        return toDtoList(friendRequestRepository.findAcceptedFriendships(currentUserId), currentUserId).stream()
                .filter(f -> !isBlockedEitherWay(currentUserId, f.otherUser().id()))
                .toList();
    }


    public record RelationshipInfo(String status, UUID requestId) {}

    /** How {@code viewerId} relates to {@code targetId}: FRIENDS, REQUEST_SENT, REQUEST_RECEIVED or NONE. */
    public RelationshipInfo relationship(UUID viewerId, UUID targetId) {
        if (areFriends(viewerId, targetId)) return new RelationshipInfo("FRIENDS", null);
        Optional<FriendRequest> outgoing = friendRequestRepository.findByRequesterIdAndAddresseeId(viewerId, targetId);
        if (outgoing.isPresent() && outgoing.get().getStatus() == FriendRequestStatus.PENDING) {
            return new RelationshipInfo("REQUEST_SENT", outgoing.get().getId());
        }
        Optional<FriendRequest> incoming = friendRequestRepository.findByRequesterIdAndAddresseeId(targetId, viewerId);
        if (incoming.isPresent() && incoming.get().getStatus() == FriendRequestStatus.PENDING) {
            return new RelationshipInfo("REQUEST_RECEIVED", incoming.get().getId());
        }
        return new RelationshipInfo("NONE", null);
    }

    /** Users the caller has blocked, so the UI can offer "Unblock". */
    public List<UserSummaryDto> listBlocked(UUID currentUserId) {
        return userBlockRepository.findByBlockerId(currentUserId).stream()
                .map(b -> userRepository.findById(b.getBlockedId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(u -> new UserSummaryDto(u.getId(), u.isDeleted() ? "Deleted User" : u.getUsername(),
                        u.getPublicId(), u.getProfilePictureUrl(), u.isDeleted()))
                .toList();
    }

    public boolean areFriends(UUID userA, UUID userB) {
        return friendRequestRepository.areFriends(userA, userB) && !isBlockedEitherWay(userA, userB);
    }

    public boolean isBlockedEitherWay(UUID userA, UUID userB) {
        return userBlockRepository.existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(userA, userB, userB, userA);
    }

    @Transactional
    public FriendProfileDto getFriendProfile(UUID viewerId, UUID friendId) {
        if (viewerId.equals(friendId) || !friendRequestRepository.areFriends(viewerId, friendId)) {
            throw new ForbiddenActionException("You can only view profiles of your friends");
        }
        User other = getActive(friendId);
        return new FriendProfileDto(other.getId(), other.getUsername(), other.getPublicId(), other.getProfilePictureUrl());
    }

    @Transactional
    public void removeFriend(UUID currentUserId, UUID friendId) {
        if (currentUserId.equals(friendId)) throw new ForbiddenActionException("Invalid friend");
        FriendRequest friendship = friendRequestRepository.findAcceptedFriendshipBetween(currentUserId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found"));
        friendRequestRepository.delete(friendship);
    }

    @Transactional
    public void block(UUID currentUserId, UUID friendId) {
        if (currentUserId.equals(friendId)) throw new ForbiddenActionException("You can't block yourself");
        getActive(friendId);
        if (!friendRequestRepository.areFriends(currentUserId, friendId)) {
            throw new ForbiddenActionException("You can only block a friend");
        }
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(currentUserId, friendId)) {
            userBlockRepository.save(UserBlock.builder().blockerId(currentUserId).blockedId(friendId).build());
        }
    }

    @Transactional
    public void unblock(UUID currentUserId, UUID friendId) {
        userBlockRepository.deleteByBlockerIdAndBlockedId(currentUserId, friendId);
    }

    private User resolveTarget(String identifier, boolean viaId) {
        if (viaId) {
            return userRepository.findByPublicId(identifier.trim())
                    .filter(u -> !u.isDeleted())
                    .orElseThrow(() -> new ResourceNotFoundException("No user found with that ID"));
        }
        // Contact = phone number or email
        Optional<User> byPhone = userRepository.findByPhoneNumber(identifier.trim());
        if (byPhone.isPresent() && !byPhone.get().isDeleted()) {
            return byPhone.get();
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(identifier.trim()).filter(u -> !u.isDeleted());
        if (byEmail.isPresent()) {
            return byEmail.get();
        }
        // Usernames are public (they are the /chat/{username} link), so they are a valid way to find someone.
        return userRepository.findByUsernameIgnoreCase(identifier.trim())
                .filter(u -> !u.isDeleted() && u.isAccountEnabled())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with that username, email or phone"));
    }

    private User getActive(UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.isDeleted()) throw new ResourceNotFoundException("User not found");
        return u;
    }

    private FriendRequestDto toDto(FriendRequest fr, UUID viewerId) {
        boolean incoming = fr.getAddresseeId().equals(viewerId);
        UUID otherId = incoming ? fr.getRequesterId() : fr.getAddresseeId();
        User other = userRepository.findById(otherId).orElse(null);
        return toDto(fr, viewerId, other);
    }

    /**
     * Batch version of toDto(): the single-item version ran one extra findById query
     * per row, so a friend list or incoming-request list of N items meant N+1 queries.
     * This loads every "other user" referenced by the whole list in one query instead.
     */
    private List<FriendRequestDto> toDtoList(List<FriendRequest> requests, UUID viewerId) {
        if (requests.isEmpty()) return List.of();

        List<UUID> otherIds = requests.stream()
                .map(fr -> fr.getAddresseeId().equals(viewerId) ? fr.getRequesterId() : fr.getAddresseeId())
                .distinct()
                .toList();

        Map<UUID, User> usersById = userRepository.findAllById(otherIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, Function.identity()));

        return requests.stream()
                .map(fr -> {
                    UUID otherId = fr.getAddresseeId().equals(viewerId) ? fr.getRequesterId() : fr.getAddresseeId();
                    return toDto(fr, viewerId, usersById.get(otherId));
                })
                .toList();
    }

    private FriendRequestDto toDto(FriendRequest fr, UUID viewerId, User other) {
        boolean incoming = fr.getAddresseeId().equals(viewerId);
        UUID otherId = incoming ? fr.getRequesterId() : fr.getAddresseeId();

        UserSummaryDto summary = other == null
                ? new UserSummaryDto(otherId, "Deleted User", "", null, true)
                : new UserSummaryDto(other.getId(), other.isDeleted() ? "Deleted User" : other.getUsername(),
                    other.getPublicId(), other.getProfilePictureUrl(), other.isDeleted());

        return new FriendRequestDto(fr.getId(), summary, fr.getStatus().name(), incoming, fr.getCreatedAt());
    }
}
