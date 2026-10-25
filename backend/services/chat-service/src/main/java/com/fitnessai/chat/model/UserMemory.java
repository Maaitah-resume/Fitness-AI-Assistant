package com.fitnessai.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** A durable fact the coach learned about the user in any conversation ("Trains before work."). */
@Entity
@Table(name = "user_memories", indexes = @Index(name = "idx_memory_user", columnList = "user_id, created_at"))
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 300)
    private String fact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserMemory() {
    }

    public UserMemory(Long userId, String fact) {
        this.userId = userId;
        this.fact = fact;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFact() { return fact; }
    public Instant getCreatedAt() { return createdAt; }
}
