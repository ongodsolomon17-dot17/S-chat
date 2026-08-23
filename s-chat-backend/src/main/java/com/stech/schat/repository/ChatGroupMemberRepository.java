package com.stech.schat.repository;

import com.stech.schat.model.ChatGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatGroupMemberRepository extends JpaRepository<ChatGroupMember, UUID> {
    Optional<ChatGroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
    List<ChatGroupMember> findByGroupIdOrderByJoinedAtAsc(UUID groupId);
    List<ChatGroupMember> findByUserIdOrderByJoinedAtDesc(UUID userId);
}
