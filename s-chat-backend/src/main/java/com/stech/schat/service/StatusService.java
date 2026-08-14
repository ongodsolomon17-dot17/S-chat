package com.stech.schat.service;

import com.stech.schat.dto.FriendRequestDto;
import com.stech.schat.dto.StatusPostDto;
import com.stech.schat.dto.UserSummaryDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.StatusPost;
import com.stech.schat.model.User;
import com.stech.schat.repository.StatusPostRepository;
import com.stech.schat.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StatusService {

    private final StatusPostRepository statusPostRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final FriendService friendService;

    public StatusService(StatusPostRepository statusPostRepository, UserRepository userRepository,
                          StorageService storageService, FriendService friendService) {
        this.statusPostRepository = statusPostRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.friendService = friendService;
    }

    @Transactional
    public StatusPostDto create(UUID userId, MultipartFile file, String caption) throws Exception {
        String url = storageService.upload("status", file);
        StatusPost post = StatusPost.builder()
                .userId(userId)
                .mediaUrl(url)
                .caption(caption)
                .build();
        statusPostRepository.save(post);
        return toDto(post);
    }

    /** Active (non-expired) statuses from the current user's accepted friends, newest first. */
    public List<StatusPostDto> friendsFeed(UUID currentUserId) {
        List<UUID> friendIds = friendService.listFriends(currentUserId).stream()
                .map(FriendRequestDto::otherUser).map(UserSummaryDto::id).toList();
        if (friendIds.isEmpty()) return List.of();
        return statusPostRepository.findActiveForUsers(friendIds, Instant.now())
                .stream().map(this::toDto).toList();
    }

    public List<StatusPostDto> myStatuses(UUID userId) {
        return statusPostRepository.findActiveByUser(userId, Instant.now())
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(UUID userId, UUID statusId) {
        StatusPost post = statusPostRepository.findById(statusId)
                .orElseThrow(() -> new ResourceNotFoundException("Status not found"));
        if (!post.getUserId().equals(userId)) {
            throw new ForbiddenActionException("You can only delete your own status");
        }
        statusPostRepository.delete(post);
    }

    private StatusPostDto toDto(StatusPost post) {
        User author = userRepository.findById(post.getUserId()).orElse(null);
        UserSummaryDto authorDto = author == null
                ? new UserSummaryDto(post.getUserId(), "Deleted User", "", null, true)
                : new UserSummaryDto(author.getId(), author.isDeleted() ? "Deleted User" : author.getUsername(),
                    author.getPublicId(), author.getProfilePictureUrl(), author.isDeleted());

        return new StatusPostDto(post.getId(), authorDto, post.getMediaUrl(), post.getCaption(),
                post.getCreatedAt(), post.getExpiresAt());
    }
}
