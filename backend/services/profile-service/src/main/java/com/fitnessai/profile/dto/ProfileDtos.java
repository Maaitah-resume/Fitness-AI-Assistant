package com.fitnessai.profile.dto;

import com.fitnessai.profile.model.Profile;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    // Partial update: null fields are left unchanged. Enum-ish fields are
    // accepted case-insensitively ("muscle_gain", "MUSCLE_GAIN") so the
    // frontend and chat-service's profile-building flow can send either.
    public record ProfileUpdateRequest(
            @Min(10) @Max(100) Integer age,
            @Pattern(regexp = "(?i)male|female|other") String gender,
            @DecimalMin("80") @DecimalMax("250") Double heightCm,
            @DecimalMin("25") @DecimalMax("350") Double weightKg,
            @Pattern(regexp = "(?i)muscle_gain|fat_loss|endurance|general_fitness") String goal,
            @Pattern(regexp = "(?i)beginner|intermediate|advanced") String level) {

        public void applyTo(Profile p) {
            if (age != null) p.setAge(age);
            if (gender != null) p.setGender(Profile.Gender.valueOf(gender.toUpperCase(Locale.ROOT)));
            if (heightCm != null) p.setHeightCm(heightCm);
            if (weightKg != null) p.setWeightKg(weightKg);
            if (goal != null) p.setGoal(Profile.Goal.valueOf(goal.toUpperCase(Locale.ROOT)));
            if (level != null) p.setFitnessLevel(Profile.FitnessLevel.valueOf(level.toUpperCase(Locale.ROOT)));
        }
    }

    public record ProfileResponse(
            Long userId,
            Integer age,
            String gender,
            Double heightCm,
            Double weightKg,
            Double bmi,
            String goal,
            String level,
            boolean complete,
            Instant updatedAt) {

        public static ProfileResponse from(Profile p) {
            return new ProfileResponse(p.getUserId(), p.getAge(), lower(p.getGender()), p.getHeightCm(),
                    p.getWeightKg(), p.bmi(), lower(p.getGoal()), lower(p.getFitnessLevel()),
                    p.getAge() != null && p.getGender() != null && p.getGoal() != null && p.getFitnessLevel() != null,
                    p.getUpdatedAt());
        }

        public static ProfileResponse empty(long userId) {
            return from(new Profile(userId));
        }

        private static String lower(Enum<?> e) {
            return e == null ? null : e.name().toLowerCase(Locale.ROOT);
        }
    }

    public record ProfileStats(
            long totalProfiles,
            Double averageAge,
            Map<String, Long> byGoal,
            Map<String, Long> byLevel,
            Map<String, Long> byGender) {
    }
}
