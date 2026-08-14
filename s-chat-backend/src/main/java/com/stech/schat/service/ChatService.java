package com.stech.schat.service;

import com.stech.schat.dto.ChatMessageDto;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.model.ChatMessage;
import com.stech.schat.repository.ChatMessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final FriendService friendService;

    public ChatService(ChatMessageRepository chatMessageRepository, FriendService friendService) {
        this.chatMessageRepository = chatMessageRepository;
        this.friendService = friendService;
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID senderId, UUID receiverId, String content, String attachmentUrl) {
        // Only friends can message each other — this is enforced here, not just in the UI,
        // since the WebSocket handler and the REST fallback both funnel through this method.
        if (!friendService.areFriends(senderId, receiverId)) {
            throw new ForbiddenActionException("You can only message users you're friends with");
        }

        ChatMessage message = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content)
                .attachmentUrl(attachmentUrl)
                .build();

        chatMessageRepository.save(message);
        return toDto(message);
    }

    @Transactional
    public void markDelivered(UUID messageId) {
        chatMessageRepository.findById(messageId).ifPresent(m -> {
            m.setDeliveredAt(Instant.now());
            chatMessageRepository.save(m);
        });
    }

    public List<ChatMessageDto> getConversation(UUID userA, UUID userB, int page, int size) {
        if (!friendService.areFriends(userA, userB)) {
            throw new ForbiddenActionException("You can only view chats with users you are friends with");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return chatMessageRepository
                .findConversation(userA, userB, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "sentAt")))
                .stream().map(this::toDto).toList();
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(m.getId(), m.getSenderId(), m.getReceiverId(), m.getContent(),
                m.getAttachmentUrl(), m.getSentAt(), m.getReadAt());
    }
}
