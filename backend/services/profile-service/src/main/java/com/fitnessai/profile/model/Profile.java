package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "profiles")
public class Profile {

    public enum Gender { MALE, FEMALE, OTHER }

    public enum Goal { MUSCLE_GAIN, FAT_LOSS, ENDURANCE, GENERAL_FITNESS }

    public enum FitnessLevel { BEGINNER, INTERMEDIATE, ADVANCED }

    // Logical reference to user-service's users.id — no cross-service FK.
    @Id
    @Column(name = "user_id")
    private Long userId;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "height_cm")
    private Double heightCm;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "fitness_level")
    private FitnessLevel fitnessLevel;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(Long userId) {
        this.userId = userId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Double bmi() {
        if (heightCm == null || weightKg == null || heightCm <= 0) {
            return null;
        }
        double m = heightCm / 100.0;
        return Math.round(weightKg / (m * m) * 10.0) / 10.0;
    }

    public Long getUserId() { return userId; }
    public Integer getAge() { return age; }
    public Gender getGender() { return gender; }
    public Double getHeightCm() { return heightCm; }
    public Double getWeightKg() { return weightKg; }
    public Goal getGoal() { return goal; }
    public FitnessLevel getFitnessLevel() { return fitnessLevel; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAge(Integer age) { this.age = age; }
    public void setGender(Gender gender) { this.gender = gender; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public void setGoal(Goal goal) { this.goal = goal; }
    public void setFitnessLevel(FitnessLevel fitnessLevel) { this.fitnessLevel = fitnessLevel; }
}
