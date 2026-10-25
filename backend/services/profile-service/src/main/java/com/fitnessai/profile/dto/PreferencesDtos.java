package com.fitnessai.profile.dto;

import com.fitnessai.profile.model.Preferences;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Locale;

public final class PreferencesDtos {

    private PreferencesDtos() {
    }

    /** Dietary styles the coach respects; an empty list clears the diet. */
    public static final String DIET_CODES = "(?i)halal|vegetarian|vegan|pescatarian|lactose_free|gluten_free|keto";

    /**
     * Partial update: null fields are left unchanged. trainingDays 0 and a blank injuries string
     * clear those fields, so the settings form can unset them. Enum values are case-insensitive.
     */
    public record PreferencesUpdate(
            @Pattern(regexp = "(?i)metric|imperial") String units,
            @Pattern(regexp = "(?i)short|detailed") String answerLength,
            @Pattern(regexp = "(?i)friendly|strict") String tone,
            @Min(0) @Max(7) Integer trainingDays,
            @Pattern(regexp = "(?i)full_gym|dumbbells|home|bodyweight|none") String equipment,
            @Size(max = 300) String injuries,
            @Size(max = 300) String allergies,
            List<@Pattern(regexp = DIET_CODES) String> diet) {

        public void applyTo(Preferences p) {
            if (units != null) p.setUnits(Preferences.Units.valueOf(upper(units)));
            if (answerLength != null) p.setAnswerLength(Preferences.AnswerLength.valueOf(upper(answerLength)));
            if (tone != null) p.setTone(Preferences.Tone.valueOf(upper(tone)));
            if (trainingDays != null) p.setTrainingDays(trainingDays == 0 ? null : trainingDays);
            if (equipment != null) p.setEquipment("none".equalsIgnoreCase(equipment) ? null : Preferences.Equipment.valueOf(upper(equipment)));
            if (injuries != null) p.setInjuries(injuries.isBlank() ? null : injuries.strip());
            if (allergies != null) p.setAllergies(allergies.isBlank() ? null : allergies.strip());
            if (diet != null) {
                String codes = String.join(",", diet.stream().map(d -> d.toLowerCase(Locale.ROOT)).distinct().toList());
                p.setDiet(codes.isEmpty() ? null : codes);
            }
        }

        private static String upper(String s) {
            return s.toUpperCase(Locale.ROOT);
        }
    }

    public record PreferencesView(String units, String answerLength, String tone, Integer trainingDays,
                                  String equipment, String injuries, String allergies, List<String> diet) {

        public static PreferencesView from(Preferences p) {
            return new PreferencesView(lower(p.getUnits()), lower(p.getAnswerLength()), lower(p.getTone()),
                    p.getTrainingDays(), lower(p.getEquipment()), p.getInjuries(), p.getAllergies(),
                    p.getDiet() == null ? List.of() : List.of(p.getDiet().split(",")));
        }

        private static String lower(Enum<?> e) {
            return e == null ? null : e.name().toLowerCase(Locale.ROOT);
        }
    }
}
