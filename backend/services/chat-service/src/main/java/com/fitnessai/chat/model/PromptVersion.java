package com.fitnessai.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** One saved version of the coach's base instructions; the newest row is the one in use. */
@Entity
@Table(name = "prompt_versions")
public class PromptVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10000)
    private String text;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", length = 50)
    private String createdByName;

    /** What the admin did: "edit", "reset" or "restore #id". */
    @Column(length = 40)
    private String action;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromptVersion() {
    }

    public PromptVersion(String text, Long createdBy, String createdByName, String action) {
        this.text = text;
        this.createdBy = createdBy;
        this.createdByName = createdByName;
        this.action = action;
    }

    @PrePersist
    void stamp() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getText() { return text; }
    public Long getCreatedBy() { return createdBy; }
    public String getCreatedByName() { return createdByName; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
