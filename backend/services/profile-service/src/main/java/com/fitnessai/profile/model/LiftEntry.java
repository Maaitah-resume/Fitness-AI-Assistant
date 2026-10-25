package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Locale;

/** One logged set (or block of identical sets) of an exercise on a given day. */
@Entity
@Table(name = "lift_entries", indexes = @Index(columnList = "user_id"))
public class LiftEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    /** As the user wrote it, for display. */
    @Column(nullable = false, length = 60)
    private String exercise;

    /** Lower-cased, single-spaced name: groups "Bench press" and "bench  Press" together. */
    @Column(name = "exercise_key", nullable = false, length = 60)
    private String exerciseKey;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(nullable = false)
    private int reps;

    private Integer sets;

    protected LiftEntry() {
    }

    public LiftEntry(Long userId, LocalDate date, String exercise, double weightKg, int reps, Integer sets) {
        this.userId = userId;
        this.date = date;
        this.exercise = exercise.strip().replaceAll("\\s+", " ");
        this.exerciseKey = keyOf(exercise);
        this.weightKg = weightKg;
        this.reps = reps;
        this.sets = sets;
    }

    public static String keyOf(String exercise) {
        return exercise.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Epley estimate of the one-rep max; a single is its own max. */
    public double estimatedOneRepMax() {
        return reps <= 1 ? weightKg : weightKg * (1 + reps / 30.0);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public String getExercise() { return exercise; }
    public String getExerciseKey() { return exerciseKey; }
    public double getWeightKg() { return weightKg; }
    public int getReps() { return reps; }
    public Integer getSets() { return sets; }
}
