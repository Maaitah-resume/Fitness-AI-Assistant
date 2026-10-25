package com.fitnessai.profile.dto;

import com.fitnessai.profile.model.MealEntry;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class NutritionDtos {

    private NutritionDtos() {
    }

    /** date defaults to today; source is "manual" or "photo". */
    public record MealRequest(
            @PastOrPresent LocalDate date,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(0) @Max(5000) Integer calories,
            @NotNull @DecimalMin("0") @DecimalMax("400") Double proteinG,
            @DecimalMin("0") @DecimalMax("800") Double carbsG,
            @DecimalMin("0") @DecimalMax("400") Double fatG,
            @Pattern(regexp = "(?i)manual|photo") String source) {

        public MealEntry.Source sourceOrManual() {
            return source == null ? MealEntry.Source.MANUAL : MealEntry.Source.valueOf(source.toUpperCase(Locale.ROOT));
        }
    }

    public record MealView(Long id, LocalDate date, String name, int calories, double proteinG, Double carbsG, Double fatG,
                           String source) {
        public static MealView from(MealEntry m) {
            return new MealView(m.getId(), m.getDate(), m.getName(), m.getCalories(), m.getProteinG(), m.getCarbsG(),
                    m.getFatG(), m.getSource().name().toLowerCase(Locale.ROOT));
        }
    }

    public record DayTotal(LocalDate date, int calories, double proteinG, int meals) {
    }

    /** Meals from the last 30 days (newest first) and one total per logged day (oldest first). */
    public record NutritionView(List<MealView> meals, List<DayTotal> days) {
    }
}
