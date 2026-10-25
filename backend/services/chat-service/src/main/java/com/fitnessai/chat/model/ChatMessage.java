package com.fitnessai.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_msg_conversation", columnList = "conversation_id"),
        @Index(name = "idx_msg_user_created", columnList = "user_id, created_at")
})
public class ChatMessage {

    public enum Role { USER, ASSISTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** JSON list of the sources an assistant reply cited; null when it cited none. */
    @Column(columnDefinition = "TEXT")
    private String citations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(Long conversationId, Long userId, Role role, String content) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.content = content;
    }

    public ChatMessage(Long conversationId, Long userId, Role role, String content, String citations) {
        this(conversationId, userId, role, content);
        this.citations = citations;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public Long getUserId() { return userId; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getCitations() { return citations; }
    public Instant getCreatedAt() { return createdAt; }
}
