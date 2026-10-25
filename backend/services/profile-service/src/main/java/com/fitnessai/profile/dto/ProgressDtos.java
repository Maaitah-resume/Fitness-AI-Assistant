package com.fitnessai.profile.dto;

import com.fitnessai.profile.model.LiftEntry;
import com.fitnessai.profile.model.WeightEntry;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class ProgressDtos {

    private ProgressDtos() {
    }

    /** date defaults to today when omitted. */
    public record WeightRequest(
            @PastOrPresent LocalDate date,
            @NotNull @DecimalMin("25") @DecimalMax("350") Double weightKg) {
    }

    /** weightKg may be 0 for bodyweight exercises; date defaults to today. */
    public record LiftRequest(
            @PastOrPresent LocalDate date,
            @NotBlank @Size(max = 60) String exercise,
            @NotNull @DecimalMin("0") @DecimalMax("500") Double weightKg,
            @NotNull @Min(1) @Max(100) Integer reps,
            @Min(1) @Max(20) Integer sets) {
    }

    public record WeightView(Long id, LocalDate date, double weightKg) {
        public static WeightView from(WeightEntry e) {
            return new WeightView(e.getId(), e.getDate(), e.getWeightKg());
        }
    }

    /** personalRecord: this set beat every earlier entry of the exercise (by estimated 1RM). */
    public record LiftView(Long id, LocalDate date, String exercise, double weightKg, int reps, Integer sets,
                           double estimatedOneRepMax, boolean personalRecord) {
        public static LiftView from(LiftEntry e, boolean personalRecord) {
            return new LiftView(e.getId(), e.getDate(), e.getExercise(), e.getWeightKg(), e.getReps(), e.getSets(),
                    Math.round(e.estimatedOneRepMax() * 10) / 10.0, personalRecord);
        }
    }

    /** Everything the progress page needs, oldest first, plus the coach-facing summary. */
    public record ProgressView(List<WeightView> weights, List<LiftView> lifts, String summary) {
    }

    public record ProgressSummary(String summary) {
    }
}
