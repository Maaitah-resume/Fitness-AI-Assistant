package com.fitnessai.profile.model;

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
import java.time.LocalDate;

/** One logged meal: macros typed by the user or estimated from a photo (the photo itself isn't kept). */
@Entity
@Table(name = "meal_entries", indexes = @Index(columnList = "user_id"))
public class MealEntry {

    public enum Source { MANUAL, PHOTO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int calories;

    @Column(name = "protein_g", nullable = false)
    private double proteinG;

    @Column(name = "carbs_g")
    private Double carbsG;

    @Column(name = "fat_g")
    private Double fatG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MealEntry() {
    }

    public MealEntry(Long userId, LocalDate date, String name, int calories, double proteinG, Double carbsG, Double fatG, Source source) {
        this.userId = userId;
        this.date = date;
        this.name = name.strip();
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.source = source;
    }

    @PrePersist
    void stamp() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public String getName() { return name; }
    public int getCalories() { return calories; }
    public double getProteinG() { return proteinG; }
    public Double getCarbsG() { return carbsG; }
    public Double getFatG() { return fatG; }
    public Source getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
