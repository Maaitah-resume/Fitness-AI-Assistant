package com.fitnessai.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/** One user's report for one Monday-to-Sunday week; the PDF itself lives in file-service (Plans). */
@Entity
@Table(name = "weekly_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "week_start"}))
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "file_id")
    private Long fileId;

    @Column(length = 200)
    private String filename;

    @Column(name = "download_url", length = 200)
    private String downloadUrl;

    @Column(length = 2000)
    private String summary;

    /** false when Gemini was unavailable and the report was written from the numbers alone. */
    @Column(name = "ai_generated", nullable = false)
    private boolean aiGenerated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WeeklyReport() {
    }

    public WeeklyReport(Long userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
    }

    @PrePersist
    void stamp() {
        createdAt = Instant.now();
    }

    public void fill(Long fileId, String filename, String downloadUrl, String summary, boolean aiGenerated) {
        this.fileId = fileId;
        this.filename = filename;
        this.downloadUrl = downloadUrl;
        this.summary = summary == null ? null : summary.length() > 2000 ? summary.substring(0, 2000) : summary;
        this.aiGenerated = aiGenerated;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getWeekStart() { return weekStart; }
    public Long getFileId() { return fileId; }
    public String getFilename() { return filename; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getSummary() { return summary; }
    public boolean isAiGenerated() { return aiGenerated; }
    public Instant getCreatedAt() { return createdAt; }
}
