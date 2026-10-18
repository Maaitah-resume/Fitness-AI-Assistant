package com.fitnessai.chat.repository;

import com.fitnessai.chat.model.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable page);

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Rows of [userId, conversationCount]. */
    @Query("select c.userId, count(c) from Conversation c group by c.userId")
    List<Object[]> countsByUser();

    @Query("select c.id from Conversation c where c.userId = :userId")
    List<Long> findIdsByUserId(Long userId);

    @Modifying
    @Query("delete from Conversation c where c.userId = :userId")
    void deleteByUserId(Long userId);
}
