package com.stech.schat.service;

import com.stech.schat.dto.ChatMessageDto;
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
    private final ChatService chatService;

    public StatusService(StatusPostRepository statusPostRepository, UserRepository userRepository,
                          StorageService storageService, FriendService friendService, ChatService chatService) {
        this.statusPostRepository = statusPostRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.friendService = friendService;
        this.chatService = chatService;
    }

    /**
     * A status reply is just a normal chat message with replyToStatusId set — it reuses
     * ChatService.sendMessage() (friendship check, persistence, realtime push to the
     * status author) instead of a parallel messaging system, per the feature spec.
     */
    @Transactional
    public ChatMessageDto reply(UUID currentUserId, UUID statusId, String content) {
        StatusPost post = statusPostRepository.findById(statusId)
                .orElseThrow(() -> new ResourceNotFoundException("Status not found"));
        if (post.getExpiresAt().isBefore(Instant.now())) {
            throw new ResourceNotFoundException("Status not found");
        }
        ChatMessageDto dto = chatService.sendMessage(currentUserId, post.getUserId(), content, null, null, statusId);
        chatService.pushToOtherParticipant(dto, currentUserId);
        return dto;
    }

    private static final String DEFAULT_TEXT_STATUS_BG = "#0aa89a";

    @Transactional
    public StatusPostDto create(UUID userId, MultipartFile file, String caption, String textContent, String backgroundColor) throws Exception {
        boolean hasFile = file != null && !file.isEmpty();
        String safeText = textContent == null ? "" : textContent.trim();
        String safeCaption = caption == null ? "" : caption.trim();
        boolean hasText = !safeText.isBlank();
        if (hasFile && hasText) throw new IllegalArgumentException("Choose either media or text for a status");
        if (safeText.length() > 700) throw new IllegalArgumentException("Status text is too long");
        if (safeCaption.length() > 280) throw new IllegalArgumentException("Status caption is too long");
        if (backgroundColor != null && !backgroundColor.isBlank()
                && !backgroundColor.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid status background color");
        }
        if (!hasFile && !hasText) {
            throw new IllegalArgumentException("A status needs a photo, video, voice note, or text");
        }

        StatusPost.StatusPostBuilder builder = StatusPost.builder().userId(userId);
        if (hasFile) {
            String url = storageService.upload("status", file);
            builder.mediaUrl(url).caption(safeCaption.isBlank() ? null : safeCaption);
        } else {
            builder.textContent(safeText)
                    .backgroundColor(backgroundColor != null && !backgroundColor.isBlank() ? backgroundColor : DEFAULT_TEXT_STATUS_BG);
        }

        StatusPost post = builder.build();
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
        if (post.getMediaUrl() != null) {
            try {
                storageService.delete(post.getMediaUrl());
            } catch (Exception ex) {
                // Row is already gone either way — a stray file in the bucket is a much
                // smaller problem than blocking the delete the user actually asked for.
                log.warn("Could not delete media for manually-deleted status {}: {}", post.getId(), ex.getMessage());
            }
        }
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
            if (post.getMediaUrl() == null) continue; // text-only status has no media to clean up
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
                post.getTextContent(), post.getBackgroundColor(), post.getCreatedAt(), post.getExpiresAt());
    }
}
