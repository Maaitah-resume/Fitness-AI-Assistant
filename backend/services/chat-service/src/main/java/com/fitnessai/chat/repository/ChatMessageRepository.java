package com.fitnessai.chat.repository;

import com.fitnessai.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Newest first — callers reverse for chronological order. */
    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable page);

    long countByConversationIdAndRole(Long conversationId, ChatMessage.Role role);

    long countByUserIdAndRole(Long userId, ChatMessage.Role role);

    @Query("select max(m.createdAt) from ChatMessage m where m.userId = :userId")
    Instant lastActivity(Long userId);

    @Query("select m.createdAt from ChatMessage m where m.userId = :userId and m.role = :role and m.createdAt >= :since")
    List<Instant> messageTimesSince(Long userId, ChatMessage.Role role, Instant since);

    @Query("select m.createdAt from ChatMessage m where m.role = :role and m.createdAt >= :since")
    List<Instant> allMessageTimesSince(ChatMessage.Role role, Instant since);

    long countByRole(ChatMessage.Role role);

    /** Rows of [userId, messageCount, lastMessageAt] for one role, per user. */
    @Query("select m.userId, count(m), max(m.createdAt) from ChatMessage m where m.role = :role group by m.userId")
    List<Object[]> activityByUser(ChatMessage.Role role);

    @Query("select count(distinct m.userId) from ChatMessage m where m.createdAt >= :since")
    long activeUsersSince(Instant since);

    @Modifying
    @Query("delete from ChatMessage m where m.conversationId = :conversationId")
    void deleteByConversationId(Long conversationId);

    @Modifying
    @Query("delete from ChatMessage m where m.userId = :userId")
    void deleteByUserId(Long userId);
}
