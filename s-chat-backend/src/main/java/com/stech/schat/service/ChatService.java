package com.stech.schat.service;

import com.stech.schat.dto.ChatListItemDto;
import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.dto.FriendRequestDto;
import com.stech.schat.dto.ReactionSummaryDto;
import com.stech.schat.dto.ReplyPreviewDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.ChatMessage;
import com.stech.schat.model.MessageHiddenFor;
import com.stech.schat.model.MessageReaction;
import com.stech.schat.repository.ChatMessageRepository;
import com.stech.schat.repository.MessageHiddenForRepository;
import com.stech.schat.repository.MessageReactionRepository;
import com.stech.schat.websocket.WebSocketSessionRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {

    /**
     * The 6 quick-pick reactions shown directly on the composer/bubble hover menu — the
     * "+" button next to them opens a full emoji picker, so any real emoji is now a valid
     * reaction. This is no longer a strict allowlist, just a "is this plausibly an emoji,
     * not arbitrary text" guard: reject anything made up solely of letters/digits/basic
     * punctuation, since genuine emoji always include at least one codepoint outside that
     * range (any category other than \p{L}/\p{N}/whitespace/basic punctuation).
     */
    private static final Set<String> QUICK_REACTIONS = Set.of("❤️", "😂", "👍", "😮", "😢", "🙏");
    private static final Pattern PLAIN_TEXT_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s.,!?'\"-]+$");
    private static final int REPLY_SNIPPET_MAX = 120;
    private static final String DELETED_PLACEHOLDER = "This message was deleted";

    private final ChatMessageRepository chatMessageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageHiddenForRepository messageHiddenForRepository;
    private final FriendService friendService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final StorageService storageService;

    public ChatService(ChatMessageRepository chatMessageRepository,
                        MessageReactionRepository messageReactionRepository,
                        MessageHiddenForRepository messageHiddenForRepository,
                        FriendService friendService,
                        WebSocketSessionRegistry sessionRegistry,
                        StorageService storageService) {
        this.chatMessageRepository = chatMessageRepository;
        this.messageReactionRepository = messageReactionRepository;
        this.messageHiddenForRepository = messageHiddenForRepository;
        this.friendService = friendService;
        this.sessionRegistry = sessionRegistry;
        this.storageService = storageService;
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID senderId, UUID receiverId, String content, String attachmentUrl,
                                       UUID replyToMessageId, UUID replyToStatusId) {
        // Only friends can message each other — this is enforced here, not just in the UI,
        // since the WebSocket handler, the status-reply flow, and any REST fallback all
        // funnel through this method.
        if (!friendService.areFriends(senderId, receiverId)) {
            throw new ForbiddenActionException("You can only message users you're friends with");
        }
        String safeContent = content == null ? "" : content.trim();
        if (safeContent.length() > 5000) throw new IllegalArgumentException("Message is too long");
        if (attachmentUrl != null) {
            if (attachmentUrl.length() > 2048 || !storageService.isManagedUrl(attachmentUrl)) {
                throw new IllegalArgumentException("Invalid attachment");
            }
        }
        if (safeContent.isBlank() && attachmentUrl == null) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        ChatMessage replyTarget = null;
        if (replyToMessageId != null) {
            replyTarget = chatMessageRepository.findById(replyToMessageId)
                    .orElseThrow(() -> new ResourceNotFoundException("Message being replied to no longer exists"));
            boolean sameConversation =
                    (replyTarget.getSenderId().equals(senderId) && replyTarget.getReceiverId().equals(receiverId)) ||
                    (replyTarget.getSenderId().equals(receiverId) && replyTarget.getReceiverId().equals(senderId));
            if (!sameConversation) {
                throw new ForbiddenActionException("You can only reply to a message in this conversation");
            }
        }

        ChatMessage message = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(safeContent)
                .attachmentUrl(attachmentUrl)
                .replyToMessageId(replyToMessageId)
                .replyToStatusId(replyToStatusId)
                .build();

        chatMessageRepository.save(message);
        return toDto(message, replyTarget, List.of(), senderId);
    }

    @Transactional
    public void markDelivered(UUID messageId) {
        chatMessageRepository.findById(messageId).ifPresent(m -> {
            m.setDeliveredAt(Instant.now());
            chatMessageRepository.save(m);
        });
    }

    /**
     * Pushes a realtime frame to the *other* participant of a message if they're online,
     * and marks the message delivered when that happens. Shared by the WebSocket "chat"
     * flow and any REST-triggered send (e.g. status replies) so both deliver identically.
     */
    public void pushToOtherParticipant(ChatMessageDto dto, UUID actingUserId) {
        UUID recipient = dto.senderId().equals(actingUserId) ? dto.receiverId() : dto.senderId();
        if (!sessionRegistry.isOnline(recipient)) return;
        markDelivered(dto.id());
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "chat");
        frame.put("id", dto.id().toString());
        frame.put("senderId", dto.senderId().toString());
        frame.put("receiverId", dto.receiverId().toString());
        frame.put("content", dto.content());
        frame.put("attachmentUrl", dto.attachmentUrl() == null ? "" : dto.attachmentUrl());
        frame.put("sentAt", dto.sentAt().toString());
        if (dto.replyTo() != null) frame.put("replyTo", dto.replyTo());
        if (dto.replyToStatusId() != null) frame.put("replyToStatusId", dto.replyToStatusId().toString());
        sessionRegistry.send(recipient, frame);
    }

    @Transactional
    public void markConversationRead(UUID viewerId, UUID friendId) {
        if (!friendService.areFriends(viewerId, friendId)) {
            throw new ForbiddenActionException("You can only read chats with users you are friends with");
        }
        List<UUID> unreadIds = chatMessageRepository.findUnreadMessageIds(viewerId, friendId);
        if (unreadIds.isEmpty()) return;

        Instant now = Instant.now();
        chatMessageRepository.markReadByIds(unreadIds, now);

        // Let the sender's own UI flip their sent-ticks to "read" in realtime.
        if (sessionRegistry.isOnline(friendId)) {
            Map<String, Object> frame = new HashMap<>();
            frame.put("type", "read");
            frame.put("conversationWith", viewerId.toString());
            frame.put("messageIds", unreadIds.stream().map(UUID::toString).toList());
            frame.put("readAt", now.toString());
            sessionRegistry.send(friendId, frame);
        }
    }

    /**
     * Builds the chat-list payload with the newest persisted message for every friend.
     * Friendships that have never exchanged a message are kept at the bottom.
     */
    public List<ChatListItemDto> getChatList(UUID currentUserId) {
        List<FriendRequestDto> friends = friendService.listFriends(currentUserId);
        if (friends.isEmpty()) return List.of();

        List<UUID> friendIds = friends.stream()
                .map(f -> f.otherUser().id())
                .toList();

        Map<UUID, ChatMessage> latestByFriend = chatMessageRepository
                .findLatestMessagesForFriends(currentUserId, friendIds)
                .stream()
                .collect(Collectors.toMap(
                        m -> m.getSenderId().equals(currentUserId) ? m.getReceiverId() : m.getSenderId(),
                        Function.identity()
                ));

        Map<UUID, Long> unreadByFriend = chatMessageRepository.countUnreadPerFriend(currentUserId, friendIds).stream()
                .collect(Collectors.toMap(ChatMessageRepository.UnreadCount::getSenderId, ChatMessageRepository.UnreadCount::getCount));

        return friends.stream()
                .map(friend -> {
                    UUID friendId = friend.otherUser().id();
                    ChatMessage latest = latestByFriend.get(friendId);
                    long unread = unreadByFriend.getOrDefault(friendId, 0L);
                    if (latest == null) {
                        return new ChatListItemDto(friend.otherUser(), null, null, null, null, false, unread);
                    }
                    boolean deleted = latest.isDeleted();
                    return new ChatListItemDto(
                            friend.otherUser(),
                            deleted ? DELETED_PLACEHOLDER : latest.getContent(),
                            deleted ? null : latest.getAttachmentUrl(),
                            latest.getSentAt(),
                            latest.getSenderId(),
                            !deleted && latest.getReplyToMessageId() != null,
                            unread
                    );
                })
                .sorted((a, b) -> {
                    if (a.latestMessageAt() == null && b.latestMessageAt() == null) return 0;
                    if (a.latestMessageAt() == null) return 1;
                    if (b.latestMessageAt() == null) return -1;
                    return b.latestMessageAt().compareTo(a.latestMessageAt());
                })
                .toList();
    }

    public List<ChatMessageDto> getConversation(UUID userA, UUID userB, int page, int size) {
        if (!friendService.areFriends(userA, userB)) {
            throw new ForbiddenActionException("You can only view chats with users you are friends with");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ChatMessage> messages = chatMessageRepository
                .findConversation(userA, userB, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "sentAt")))
                .getContent();

        return hydrate(messages, userA);
    }

    /** Batch-hydrates a page of messages with reply previews and reaction summaries in O(1) extra queries. */
    private List<ChatMessageDto> hydrate(List<ChatMessage> messages, UUID viewerId) {
        if (messages.isEmpty()) return List.of();

        List<UUID> replyIds = messages.stream()
                .map(ChatMessage::getReplyToMessageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, ChatMessage> replyTargets = replyIds.isEmpty() ? Map.of()
                : chatMessageRepository.findAllByIdIn(replyIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));

        List<UUID> messageIds = messages.stream().map(ChatMessage::getId).toList();
        Map<UUID, List<MessageReaction>> reactionsByMessage = messageReactionRepository
                .findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));

        return messages.stream()
                .map(m -> toDto(m,
                        m.getReplyToMessageId() != null ? replyTargets.get(m.getReplyToMessageId()) : null,
                        reactionsByMessage.getOrDefault(m.getId(), List.of()),
                        viewerId))
                .toList();
    }

    @Transactional
    public ChatMessageDto addReaction(UUID userId, UUID messageId, String reactionType) {
        if (!isPlausibleEmoji(reactionType)) {
            throw new IllegalArgumentException("Unsupported reaction type");
        }
        ChatMessage message = requireParticipant(userId, messageId);
        if (message.isDeleted()) {
            throw new ForbiddenActionException("Cannot react to a deleted message");
        }

        MessageReaction reaction = messageReactionRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> MessageReaction.builder().messageId(messageId).userId(userId).build());
        reaction.setReactionType(reactionType);
        messageReactionRepository.save(reaction);

        ChatMessageDto dto = toDtoWithFreshState(message, userId);
        notifyReactionChange(message, userId, dto);
        return dto;
    }

    @Transactional
    public ChatMessageDto removeReaction(UUID userId, UUID messageId) {
        ChatMessage message = requireParticipant(userId, messageId);
        messageReactionRepository.deleteByMessageIdAndUserId(messageId, userId);

        ChatMessageDto dto = toDtoWithFreshState(message, userId);
        notifyReactionChange(message, userId, dto);
        return dto;
    }

    @Transactional
    public void hideForMe(UUID userId, UUID messageId) {
        // "Delete for me": no sender/receiver restriction — either participant may hide
        // their own copy. Idempotent, and never touches the message row itself, so it
        // has zero effect on the other participant or on delete-for-everyone.
        requireParticipant(userId, messageId);
        if (messageHiddenForRepository.existsByMessageIdAndUserId(messageId, userId)) return;
        messageHiddenForRepository.save(MessageHiddenFor.builder().messageId(messageId).userId(userId).build());
    }

    @Transactional
    public ChatMessageDto deleteMessage(UUID userId, UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSenderId().equals(userId)) {
            throw new ForbiddenActionException("You can only delete your own message");
        }
        if (!message.isDeleted()) {
            message.setDeletedAt(Instant.now());
            message.setDeletedBy(userId);
            chatMessageRepository.save(message);
        }

        ChatMessageDto dto = toDtoWithFreshState(message, userId);
        UUID otherParty = message.getReceiverId().equals(userId) ? message.getSenderId() : message.getReceiverId();
        if (sessionRegistry.isOnline(otherParty)) {
            sessionRegistry.send(otherParty, Map.of(
                    "type", "message_deleted",
                    "id", message.getId().toString(),
                    "conversationWith", userId.toString()
            ));
        }
        return dto;
    }

    private void notifyReactionChange(ChatMessage message, UUID actingUserId, ChatMessageDto dto) {
        UUID otherParty = message.getReceiverId().equals(actingUserId) ? message.getSenderId() : message.getReceiverId();
        if (!sessionRegistry.isOnline(otherParty)) return;
        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "reaction");
        frame.put("messageId", message.getId().toString());
        frame.put("reactions", dto.reactions());
        sessionRegistry.send(otherParty, frame);
    }

    private boolean isPlausibleEmoji(String value) {
        if (value == null || value.isBlank() || value.length() > 32) return false;
        return !PLAIN_TEXT_PATTERN.matcher(value).matches();
    }

    /** Confirms the user is either the sender or receiver of the message (never trusts the client's word for it). */
    private ChatMessage requireParticipant(UUID userId, UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
        if (!message.getSenderId().equals(userId) && !message.getReceiverId().equals(userId)) {
            throw new ForbiddenActionException("You do not have access to this message");
        }
        return message;
    }

    private ChatMessageDto toDtoWithFreshState(ChatMessage message, UUID viewerId) {
        ChatMessage replyTarget = message.getReplyToMessageId() != null
                ? chatMessageRepository.findById(message.getReplyToMessageId()).orElse(null)
                : null;
        List<MessageReaction> reactions = messageReactionRepository.findByMessageId(message.getId());
        return toDto(message, replyTarget, reactions, viewerId);
    }

    private ChatMessageDto toDto(ChatMessage m, ChatMessage replyTarget, List<MessageReaction> reactions, UUID viewerId) {
        boolean deleted = m.isDeleted();

        ReplyPreviewDto replyPreview = null;
        if (m.getReplyToMessageId() != null) {
            if (replyTarget == null) {
                replyPreview = new ReplyPreviewDto(m.getReplyToMessageId(), null, null, null, true);
            } else if (replyTarget.isDeleted()) {
                replyPreview = new ReplyPreviewDto(replyTarget.getId(), replyTarget.getSenderId(), null, null, true);
            } else {
                String snippet = replyTarget.getContent() == null ? "" : replyTarget.getContent();
                if (snippet.length() > REPLY_SNIPPET_MAX) snippet = snippet.substring(0, REPLY_SNIPPET_MAX) + "…";
                replyPreview = new ReplyPreviewDto(
                        replyTarget.getId(), replyTarget.getSenderId(), snippet,
                        replyTarget.getAttachmentUrl(), false);
            }
        }

        List<ReactionSummaryDto> reactionSummaries = reactions.stream()
                .collect(Collectors.groupingBy(MessageReaction::getReactionType))
                .entrySet().stream()
                .map(e -> new ReactionSummaryDto(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().anyMatch(r -> r.getUserId().equals(viewerId))))
                .sorted(Comparator.comparing(ReactionSummaryDto::reactionType))
                .toList();

        return new ChatMessageDto(
                m.getId(), m.getSenderId(), m.getReceiverId(),
                deleted ? DELETED_PLACEHOLDER : m.getContent(),
                deleted ? null : m.getAttachmentUrl(),
                m.getSentAt(), m.getDeliveredAt(), m.getReadAt(),
                replyPreview, m.getReplyToStatusId(),
                new ArrayList<>(reactionSummaries),
                deleted
        );
    }
}
