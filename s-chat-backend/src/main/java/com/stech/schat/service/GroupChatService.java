package com.stech.schat.service;

import com.stech.schat.dto.*;
import com.stech.schat.exception.ForbiddenActionException;
import com.stech.schat.exception.ResourceNotFoundException;
import com.stech.schat.model.ChatGroup;
import com.stech.schat.model.ChatGroupMember;
import com.stech.schat.model.ChatGroupMessage;
import com.stech.schat.model.User;
import com.stech.schat.repository.ChatGroupMemberRepository;
import com.stech.schat.repository.ChatGroupMessageRepository;
import com.stech.schat.repository.ChatGroupRepository;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.websocket.WebSocketSessionRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GroupChatService {
    private static final int MAX_MEMBERS = 50;

    private final ChatGroupRepository groupRepository;
    private final ChatGroupMemberRepository memberRepository;
    private final ChatGroupMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;
    private final StorageService storageService;
    private final WebSocketSessionRegistry sessionRegistry;

    public GroupChatService(ChatGroupRepository groupRepository,
                            ChatGroupMemberRepository memberRepository,
                            ChatGroupMessageRepository messageRepository,
                            UserRepository userRepository,
                            FriendService friendService,
                            StorageService storageService,
                            WebSocketSessionRegistry sessionRegistry) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.storageService = storageService;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional
    public GroupDetailsDto create(UUID creatorId, CreateGroupRequest request) {
        User creator = activeUser(creatorId);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) throw new IllegalArgumentException("Group name is required");
        if (name.length() > 80) throw new IllegalArgumentException("Group name is too long");

        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (request.memberIds() != null) ids.addAll(request.memberIds());
        ids.remove(creatorId);
        if (ids.size() > MAX_MEMBERS - 1) throw new IllegalArgumentException("A group can have at most 50 members");

        for (UUID id : ids) {
            activeUser(id);
            if (!friendService.areFriends(creatorId, id)) {
                throw new ForbiddenActionException("You can only add your friends to a group");
            }
        }

        String avatar = request.avatarUrl();
        if (avatar != null && !avatar.isBlank()) {
            if (avatar.length() > 512 || !storageService.isManagedUrl(avatar)) {
                throw new IllegalArgumentException("Invalid group image");
            }
        } else avatar = null;

        ChatGroup group = groupRepository.save(ChatGroup.builder()
                .name(name)
                .avatarUrl(avatar)
                .createdBy(creatorId)
                .build());

        memberRepository.save(ChatGroupMember.builder()
                .groupId(group.getId()).userId(creatorId)
                .role(ChatGroupMember.MemberRole.ADMIN).build());
        for (UUID id : ids) {
            memberRepository.save(ChatGroupMember.builder()
                    .groupId(group.getId()).userId(id)
                    .role(ChatGroupMember.MemberRole.MEMBER).build());
        }

        broadcastGroupEvent(group.getId(), Map.of("type", "group_created", "groupId", group.getId().toString()));
        return details(creatorId, group.getId());
    }

    @Transactional(readOnly = true)
    public List<GroupSummaryDto> list(UUID userId) {
        return memberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(m -> {
                    ChatGroup g = groupRepository.findById(m.getGroupId()).orElse(null);
                    if (g == null) return null;
                    return new GroupSummaryDto(g.getId(), g.getName(), g.getAvatarUrl(), g.getCreatedBy(),
                            g.getCreatedAt(), m.getRole().name(), memberRepository.findByGroupIdOrderByJoinedAtAsc(g.getId()).size());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupDetailsDto details(UUID userId, UUID groupId) {
        ChatGroup group = requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, userId);
        List<ChatGroupMember> members = memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId);
        List<UUID> ids = members.stream().map(ChatGroupMember::getUserId).toList();
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(u -> users.put(u.getId(), u));

        List<GroupMemberDto> dto = members.stream().map(m -> {
            User u = users.get(m.getUserId());
            return new GroupMemberDto(m.getUserId(), u == null || u.isDeleted() ? "Deleted User" : u.getUsername(),
                    u == null ? "" : u.getPublicId(), u == null ? null : u.getProfilePictureUrl(),
                    m.getRole().name(), m.getJoinedAt());
        }).toList();
        return new GroupDetailsDto(group.getId(), group.getName(), group.getAvatarUrl(), group.getCreatedBy(),
                group.getCreatedAt(), me.getRole().name(), dto);
    }

    @Transactional(readOnly = true)
    public List<GroupMessageDto> history(UUID userId, UUID groupId, int page, int size) {
        requireMember(groupId, userId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<ChatGroupMessage> messages = messageRepository.findByGroupIdOrderBySentAtDesc(
                groupId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "sentAt"))).getContent();

        System.out.println("========== GROUP CHAT HISTORY DEBUG ==========");
        System.out.println("User ID: " + userId);
        System.out.println("Group ID: " + groupId);
        System.out.println("Requested page: " + safePage);
        System.out.println("Requested size: " + safeSize);
        System.out.println("Messages returned from repository: " + messages.size());

        for (ChatGroupMessage message : messages) {
            System.out.println(
                    "Message ID: " + message.getId()
                            + " | Group ID: " + message.getGroupId()
                            + " | Sender ID: " + message.getSenderId()
                            + " | Content: " + message.getContent()
                            + " | Sent At: " + message.getSentAt()
            );
        }

        // Fix: Page.getContent() may return an unmodifiable list, and Collections.reverse()
        // mutates via set() internally -> UnsupportedOperationException. Copy into a mutable
        // ArrayList before reversing.
        List<ChatGroupMessage> orderedMessages = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);

        List<GroupMessageDto> result = orderedMessages.stream().map(this::toDto).toList();

        System.out.println("DTO messages returned: " + result.size());
        System.out.println("==============================================");

        return result;
    }

    @Transactional
    public GroupMessageDto sendMessage(UUID senderId, UUID groupId, String content, String attachmentUrl) {
        requireMember(groupId, senderId);
        String safe = content == null ? "" : content.trim();
        if (safe.length() > 5000) throw new IllegalArgumentException("Message is too long");
        if (attachmentUrl != null && (attachmentUrl.length() > 2048 || !storageService.isManagedUrl(attachmentUrl))) {
            throw new IllegalArgumentException("Invalid attachment");
        }
        if (safe.isBlank() && attachmentUrl == null) throw new IllegalArgumentException("Message cannot be empty");

        ChatGroupMessage message = messageRepository.save(ChatGroupMessage.builder()
                .groupId(groupId).senderId(senderId).content(safe).attachmentUrl(attachmentUrl).build());
        GroupMessageDto dto = toDto(message);

        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "group_chat");
        frame.put("id", dto.id().toString());
        frame.put("groupId", groupId.toString());
        frame.put("senderId", senderId.toString());
        frame.put("senderName", dto.senderName());
        frame.put("senderAvatarUrl", dto.senderAvatarUrl() == null ? "" : dto.senderAvatarUrl());
        frame.put("content", dto.content());
        frame.put("attachmentUrl", dto.attachmentUrl() == null ? "" : dto.attachmentUrl());
        frame.put("sentAt", dto.sentAt().toString());
        broadcastGroupEvent(groupId, frame);
        return dto;
    }

    @Transactional
    public void addMember(UUID actorId, UUID groupId, UUID userId) {
        requireAdmin(groupId, actorId);
        activeUser(userId);
        if (!friendService.areFriends(actorId, userId)) {
            throw new ForbiddenActionException("You can only add your friends to a group");
        }
        if (memberRepository.existsByGroupIdAndUserId(groupId, userId)) return;
        if (memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).size() >= MAX_MEMBERS) {
            throw new IllegalArgumentException("A group can have at most 50 members");
        }
        memberRepository.save(ChatGroupMember.builder().groupId(groupId).userId(userId).role(ChatGroupMember.MemberRole.MEMBER).build());
        notifyMembership(groupId, "group_member_added", userId);
    }

    @Transactional
    public void removeMember(UUID actorId, UUID groupId, UUID userId) {
        ChatGroup group = requireGroup(groupId);
        requireAdmin(groupId, actorId);
        ChatGroupMember target = requireMember(groupId, userId);
        if (userId.equals(group.getCreatedBy())) throw new ForbiddenActionException("The group creator cannot be removed");
        if (userId.equals(actorId)) throw new ForbiddenActionException("Use Leave group to leave the group");
        memberRepository.delete(target);
        notifyMembership(groupId, "group_member_removed", userId);
    }

    @Transactional
    public void promote(UUID actorId, UUID groupId, UUID userId) {
        ChatGroup group = requireGroup(groupId);
        requireAdmin(groupId, actorId);
        ChatGroupMember target = requireMember(groupId, userId);
        if (userId.equals(group.getCreatedBy())) return;
        target.setRole(ChatGroupMember.MemberRole.ADMIN);
        memberRepository.save(target);
        notifyMembership(groupId, "group_admin_promoted", userId);
    }

    @Transactional
    public void demote(UUID actorId, UUID groupId, UUID userId) {
        ChatGroup group = requireGroup(groupId);
        requireAdmin(groupId, actorId);
        ChatGroupMember target = requireMember(groupId, userId);
        if (userId.equals(group.getCreatedBy())) throw new ForbiddenActionException("The group creator must remain an admin");
        target.setRole(ChatGroupMember.MemberRole.MEMBER);
        memberRepository.save(target);
        notifyMembership(groupId, "group_admin_demoted", userId);
    }

    @Transactional
    public void transferOwnership(UUID actorId, UUID groupId, UUID newOwnerId) {
        ChatGroup group = requireGroup(groupId);
        if (!actorId.equals(group.getCreatedBy())) {
            throw new ForbiddenActionException("Only the group creator can transfer ownership");
        }
        if (actorId.equals(newOwnerId)) return;

        ChatGroupMember target = requireMember(groupId, newOwnerId);
        target.setRole(ChatGroupMember.MemberRole.ADMIN);
        memberRepository.save(target);

        group.setCreatedBy(newOwnerId);
        groupRepository.save(group);

        Map<String, Object> frame = new HashMap<>();
        frame.put("type", "group_ownership_transferred");
        frame.put("groupId", groupId.toString());
        frame.put("userId", newOwnerId.toString());
        broadcastGroupEvent(groupId, frame);
    }

    @Transactional
    public void leave(UUID userId, UUID groupId) {
        ChatGroup group = requireGroup(groupId);
        ChatGroupMember me = requireMember(groupId, userId);
        if (userId.equals(group.getCreatedBy())) throw new ForbiddenActionException("The group creator cannot leave; transfer admin ownership first");
        memberRepository.delete(me);
        notifyMembership(groupId, "group_member_left", userId);
    }

    private ChatGroup requireGroup(UUID groupId) {
        return groupRepository.findById(groupId).orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    }

    private ChatGroupMember requireMember(UUID groupId, UUID userId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenActionException("You are not a member of this group"));
    }

    private ChatGroupMember requireAdmin(UUID groupId, UUID userId) {
        ChatGroupMember member = requireMember(groupId, userId);
        if (member.getRole() != ChatGroupMember.MemberRole.ADMIN) {
            throw new ForbiddenActionException("Only group admins can manage members");
        }
        return member;
    }

    private User activeUser(UUID id) {
        User u = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (u.isDeleted()) throw new ResourceNotFoundException("User not found");
        return u;
    }

    private GroupMessageDto toDto(ChatGroupMessage m) {
        User u = userRepository.findById(m.getSenderId()).orElse(null);
        return new GroupMessageDto(m.getId(), m.getGroupId(), m.getSenderId(),
                u == null || u.isDeleted() ? "Deleted User" : u.getUsername(),
                u == null ? null : u.getProfilePictureUrl(),
                m.isDeleted() ? "This message was deleted" : m.getContent(),
                m.getAttachmentUrl(), m.getSentAt(), m.isDeleted());
    }

    private void notifyMembership(UUID groupId, String type, UUID affectedUserId) {
        Map<String, Object> frame = Map.of("type", type, "groupId", groupId.toString(), "userId", affectedUserId.toString());
        broadcastGroupEvent(groupId, frame);
        if (sessionRegistry.isOnline(affectedUserId)) sessionRegistry.send(affectedUserId, frame);
    }

    private void broadcastGroupEvent(UUID groupId, Map<String, Object> frame) {
        for (ChatGroupMember member : memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId)) {
            if (sessionRegistry.isOnline(member.getUserId())) sessionRegistry.send(member.getUserId(), frame);
        }
    }
}