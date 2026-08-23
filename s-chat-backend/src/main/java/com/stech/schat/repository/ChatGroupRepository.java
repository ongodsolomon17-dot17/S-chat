package com.stech.schat.repository;

import com.stech.schat.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, UUID> {
}
