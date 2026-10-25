package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** One body-weight reading per user per day; logging the same day again replaces it. */
@Entity
@Table(name = "weight_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "entry_date"}),
        indexes = @Index(columnList = "user_id"))
public class WeightEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    protected WeightEntry() {
    }

    public WeightEntry(Long userId, LocalDate date, double weightKg) {
        this.userId = userId;
        this.date = date;
        this.weightKg = weightKg;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
}
