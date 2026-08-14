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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StatusService {

    private static final Logger log = LoggerFactory.getLogger(StatusService.class);

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
        return toDtoList(statusPostRepository.findActiveForUsers(friendIds, Instant.now()));
    }

    public List<StatusPostDto> myStatuses(UUID userId) {
        return toDtoList(statusPostRepository.findActiveByUser(userId, Instant.now()));
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

    /**
     * Statuses are only ever filtered out of feeds once expired — nothing previously
     * deleted the underlying rows or their media, so the table (and the Supabase
     * bucket) only ever grew, which shows up over time as a slowly bloating feed
     * query and rising storage usage. Runs hourly; each run's media deletes are
     * best-effort and never block the DB cleanup.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void purgeExpiredStatuses() {
        Instant now = Instant.now();
        List<StatusPost> expired = statusPostRepository.findAllExpired(now);
        if (expired.isEmpty()) return;

        for (StatusPost post : expired) {
            try {
                storageService.delete(post.getMediaUrl());
            } catch (Exception ex) {
                log.warn("Could not delete media for expired status {}: {}", post.getId(), ex.getMessage());
            }
        }
        int deleted = statusPostRepository.deleteAllExpired(now);
        log.info("Purged {} expired status post(s)", deleted);
    }

    private StatusPostDto toDto(StatusPost post) {
        User author = userRepository.findById(post.getUserId()).orElse(null);
        return toDto(post, author);
    }

    /**
     * Batch version of toDto(): the single-item version ran one extra findById query
     * per post, so a feed of N statuses meant N+1 queries. This loads every author
     * referenced by the whole list in one query instead.
     */
    private List<StatusPostDto> toDtoList(List<StatusPost> posts) {
        if (posts.isEmpty()) return List.of();

        List<UUID> authorIds = posts.stream().map(StatusPost::getUserId).distinct().toList();
        Map<UUID, User> usersById = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return posts.stream().map(post -> toDto(post, usersById.get(post.getUserId()))).toList();
    }

    private StatusPostDto toDto(StatusPost post, User author) {
        UserSummaryDto authorDto = author == null
                ? new UserSummaryDto(post.getUserId(), "Deleted User", "", null, true)
                : new UserSummaryDto(author.getId(), author.isDeleted() ? "Deleted User" : author.getUsername(),
                    author.getPublicId(), author.getProfilePictureUrl(), author.isDeleted());

        return new StatusPostDto(post.getId(), authorDto, post.getMediaUrl(), post.getCaption(),
                post.getCreatedAt(), post.getExpiresAt());
    }
}
