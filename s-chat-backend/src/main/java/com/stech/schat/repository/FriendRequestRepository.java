package com.stech.schat.repository;

import com.stech.schat.model.FriendRequest;
import com.stech.schat.model.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {

    Optional<FriendRequest> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

    List<FriendRequest> findByAddresseeIdAndStatus(UUID addresseeId, FriendRequestStatus status);

    @Query("""
        SELECT fr FROM FriendRequest fr
        WHERE fr.status = com.stech.schat.model.FriendRequestStatus.ACCEPTED
          AND (fr.requesterId = :userId OR fr.addresseeId = :userId)
        """)
    List<FriendRequest> findAcceptedFriendships(@Param("userId") UUID userId);

    @Query("""
        SELECT CASE WHEN COUNT(fr) > 0 THEN true ELSE false END FROM FriendRequest fr
        WHERE fr.status = com.stech.schat.model.FriendRequestStatus.ACCEPTED
          AND ((fr.requesterId = :userA AND fr.addresseeId = :userB)
            OR (fr.requesterId = :userB AND fr.addresseeId = :userA))
        """)
    boolean areFriends(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
        SELECT fr FROM FriendRequest fr
        WHERE fr.status = com.stech.schat.model.FriendRequestStatus.ACCEPTED
          AND ((fr.requesterId = :userA AND fr.addresseeId = :userB)
            OR (fr.requesterId = :userB AND fr.addresseeId = :userA))
        """)
    Optional<FriendRequest> findAcceptedFriendshipBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);
}
