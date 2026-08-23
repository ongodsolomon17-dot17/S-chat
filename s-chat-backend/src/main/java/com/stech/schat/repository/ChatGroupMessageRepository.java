package com.stech.schat.repository;

import com.stech.schat.model.ChatGroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatGroupMessageRepository extends JpaRepository<ChatGroupMessage, UUID> {
    Page<ChatGroupMessage> findByGroupIdOrderBySentAtDesc(UUID groupId, Pageable pageable);
}
