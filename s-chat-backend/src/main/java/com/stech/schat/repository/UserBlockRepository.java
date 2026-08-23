package com.stech.schat.repository;

import com.stech.schat.model.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    boolean existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(UUID blockerA, UUID blockedA, UUID blockerB, UUID blockedB);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
