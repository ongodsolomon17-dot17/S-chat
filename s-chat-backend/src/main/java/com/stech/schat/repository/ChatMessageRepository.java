package com.stech.schat.repository;

import com.stech.schat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE (m.senderId = :userA AND m.receiverId = :userB)
           OR (m.senderId = :userB AND m.receiverId = :userA)
        ORDER BY m.sentAt DESC
        """)
    Page<ChatMessage> findConversation(@Param("userA") UUID userA, @Param("userB") UUID userB, Pageable pageable);

    /**
     * Returns the newest message for each friend in one query. The window function
     * partitions the conversation by the other participant, so the chat list can be
     * ordered by real message activity without running one query per friend.
     * PostgreSQL is the production database for S-Chat, so this native query is
     * intentionally PostgreSQL-specific.
     */
    @Query(value = """
        SELECT id, sender_id, receiver_id, content, attachment_url, sent_at, delivered_at, read_at
        FROM (
            SELECT m.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY CASE
                           WHEN m.sender_id = :userId THEN m.receiver_id
                           ELSE m.sender_id
                       END
                       ORDER BY m.sent_at DESC, m.id DESC
                   ) AS rn
            FROM chat_messages m
            WHERE (m.sender_id = :userId AND m.receiver_id IN (:friendIds))
               OR (m.receiver_id = :userId AND m.sender_id IN (:friendIds))
        ) latest
        WHERE latest.rn = 1
        """, nativeQuery = true)
    List<ChatMessage> findLatestMessagesForFriends(
            @Param("userId") UUID userId,
            @Param("friendIds") List<UUID> friendIds);
}
