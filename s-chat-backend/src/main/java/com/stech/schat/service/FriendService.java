package com.stech.schat.service;

import com.stech.schat.dto.FriendRequestDto;
import com.stech.schat.dto.SendFriendRequestRequest;
import com.stech.schat.dto.UserSummaryDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.FriendRequest;
import com.stech.schat.model.FriendRequestStatus;
import com.stech.schat.model.User;
import com.stech.schat.repository.FriendRequestRepository;
import com.stech.schat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;

    public FriendService(FriendRequestRepository friendRequestRepository, UserRepository userRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
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
        return toDto(fr, currentUserId);
    }

    public List<FriendRequestDto> listPendingIncoming(UUID currentUserId) {
        return friendRequestRepository.findByAddresseeIdAndStatus(currentUserId, FriendRequestStatus.PENDING)
                .stream().map(fr -> toDto(fr, currentUserId)).toList();
    }

    public List<FriendRequestDto> listFriends(UUID currentUserId) {
        return friendRequestRepository.findAcceptedFriendships(currentUserId)
                .stream().map(fr -> toDto(fr, currentUserId)).toList();
    }

    public boolean areFriends(UUID userA, UUID userB) {
        return friendRequestRepository.areFriends(userA, userB);
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
        return userRepository.findByEmailIgnoreCase(identifier.trim())
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with that contact info"));
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

        UserSummaryDto summary = other == null
                ? new UserSummaryDto(otherId, "Deleted User", "", null, true)
                : new UserSummaryDto(other.getId(), other.isDeleted() ? "Deleted User" : other.getUsername(),
                    other.getPublicId(), other.getProfilePictureUrl(), other.isDeleted());

        return new FriendRequestDto(fr.getId(), summary, fr.getStatus().name(), incoming, fr.getCreatedAt());
    }
}
