package com.stech.schat.repository;

import com.stech.schat.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE ((m.senderId = :userA AND m.receiverId = :userB)
           OR (m.senderId = :userB AND m.receiverId = :userA))
          AND NOT EXISTS (
              SELECT 1 FROM MessageHiddenFor h WHERE h.messageId = m.id AND h.userId = :userA
          )
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
        SELECT id, sender_id, receiver_id, content, attachment_url, sent_at, delivered_at, read_at,
               reply_to_message_id, reply_to_status_id, deleted_at, deleted_by
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
            WHERE ((m.sender_id = :userId AND m.receiver_id IN (:friendIds))
               OR (m.receiver_id = :userId AND m.sender_id IN (:friendIds)))
              AND NOT EXISTS (
                  SELECT 1 FROM chat_message_hidden_for h WHERE h.message_id = m.id AND h.user_id = :userId
              )
        ) latest
        WHERE latest.rn = 1
        """, nativeQuery = true)
    List<ChatMessage> findLatestMessagesForFriends(
            @Param("userId") UUID userId,
            @Param("friendIds") List<UUID> friendIds);

    // Batch fetch for reply-preview rendering: a page of messages can reference up to
    // that many distinct parent messages, so this avoids one findById() per reply.
    List<ChatMessage> findAllByIdIn(List<UUID> ids);

    // Read receipts: find what's unread, bulk-mark it read in one statement rather
    // than loading and saving each ChatMessage entity individually.
    @Query("""
        SELECT m.id FROM ChatMessage m
        WHERE m.receiverId = :viewerId AND m.senderId = :friendId
          AND m.readAt IS NULL AND m.deletedAt IS NULL
        """)
    List<UUID> findUnreadMessageIds(@Param("viewerId") UUID viewerId, @Param("friendId") UUID friendId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.readAt = :readAt WHERE m.id IN :ids")
    void markReadByIds(@Param("ids") List<UUID> ids, @Param("readAt") java.time.Instant readAt);

    interface UnreadCount {
        UUID getSenderId();
        long getCount();
    }

    // Batch unread-per-friend for the chat list badge — one query for every friend
    // instead of one COUNT per row.
    @Query("""
        SELECT m.senderId AS senderId, COUNT(m) AS count FROM ChatMessage m
        WHERE m.receiverId = :userId AND m.senderId IN :friendIds
          AND m.readAt IS NULL AND m.deletedAt IS NULL
        GROUP BY m.senderId
        """)
    List<UnreadCount> countUnreadPerFriend(@Param("userId") UUID userId, @Param("friendIds") List<UUID> friendIds);
}
