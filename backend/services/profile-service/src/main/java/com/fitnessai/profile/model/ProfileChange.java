package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** One change to a profile field the history timeline shows (goal, level); oldValue is null when first set. */
@Entity
@Table(name = "profile_changes", indexes = @Index(name = "idx_change_user", columnList = "user_id, changed_at"))
public class ProfileChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String field;

    @Column(name = "old_value", length = 40)
    private String oldValue;

    @Column(name = "new_value", length = 40)
    private String newValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ProfileChange() {
    }

    public ProfileChange(Long userId, String field, String oldValue, String newValue, Instant changedAt) {
        this.userId = userId;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getField() { return field; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public Instant getChangedAt() { return changedAt; }
}
