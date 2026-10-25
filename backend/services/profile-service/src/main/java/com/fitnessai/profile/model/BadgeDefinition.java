package com.fitnessai.profile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A badge an admin defined: what it looks like and the rule that earns it
 * (a {@link BadgeRule} plus its threshold, and an exercise for lift rules).
 * Users earn it automatically from their logs; nothing is coded per badge.
 */
@Entity
@Table(name = "badge_definitions")
public class BadgeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable slug used by the app (e.g. "workouts_10"); never changes after creation. */
    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "name_ar", length = 60)
    private String nameAr;

    @Column(length = 200)
    private String description;

    @Column(name = "description_ar", length = 200)
    private String descriptionAr;

    /** An icon name from the app's badge icon set (see the admin form). */
    @Column(nullable = false, length = 30)
    private String icon;

    /** One of the app's accent colours: blue, green, yellow, red, steel, signal, ember. */
    @Column(nullable = false, length = 20)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BadgeRule rule;

    @Column(nullable = false)
    private double threshold;

    /** Only for lift rules. */
    @Column(length = 60)
    private String exercise;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BadgeDefinition() {
    }

    public BadgeDefinition(String code) {
        this.code = code;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getNameAr() { return nameAr; }
    public String getDescription() { return description; }
    public String getDescriptionAr() { return descriptionAr; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public BadgeRule getRule() { return rule; }
    public double getThreshold() { return threshold; }
    public String getExercise() { return exercise; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setNameAr(String nameAr) { this.nameAr = nameAr; }
    public void setDescription(String description) { this.description = description; }
    public void setDescriptionAr(String descriptionAr) { this.descriptionAr = descriptionAr; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setColor(String color) { this.color = color; }
    public void setRule(BadgeRule rule) { this.rule = rule; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public void setExercise(String exercise) { this.exercise = exercise; }
    public void setActive(boolean active) { this.active = active; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
