package com.fitnessai.file.model;

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
@Table(name = "files", indexes = @Index(name = "idx_files_owner", columnList = "owner_id, created_at"))
public class FileRecord {

    public enum Type { UPLOAD, GENERATED }

    /**
     * UPLOAD: PROCESSING -> INDEXED | NO_TEXT | FAILED.
     * GENERATED: READY.
     */
    public enum Status { PROCESSING, INDEXED, NO_TEXT, FAILED, READY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 160)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    /** Admin uploads marked shared feed the knowledge base for every user. */
    @Column(nullable = false)
    private boolean shared;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FileRecord() {
    }

    public FileRecord(Long ownerId, String filename, Type type, Status status, boolean shared, long sizeBytes,
                      String storageKey) {
        this.ownerId = ownerId;
        this.filename = filename;
        this.type = type;
        this.status = status;
        this.shared = shared;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markIndexed(int chunks) {
        status = Status.INDEXED;
        chunkCount = chunks;
        error = null;
    }

    public void markNoText() {
        status = Status.NO_TEXT;
        chunkCount = 0;
        error = "No extractable text (scanned/image-only PDFs aren't supported yet).";
    }

    public void markFailed(String reason) {
        status = Status.FAILED;
        error = reason == null ? "Unknown error" : reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    /** knowledge-service document id for this file's chunks. */
    public String knowledgeDocumentId() {
        return "file-" + id;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getFilename() { return filename; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public boolean isShared() { return shared; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public Integer getChunkCount() { return chunkCount; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
}
