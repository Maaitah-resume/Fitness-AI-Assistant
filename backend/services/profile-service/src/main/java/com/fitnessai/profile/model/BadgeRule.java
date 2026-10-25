package com.fitnessai.profile.model;

/**
 * What a badge measures. The admin picks one and sets a threshold; the
 * achievements engine evaluates it against each user's logs.
 * unit describes the threshold; needsExercise marks lift rules.
 */
public enum BadgeRule {
    WORKOUTS("workouts", false),
    PERSONAL_RECORDS("personal records", false),
    WEIGH_INS("weigh-ins", false),
    MEALS_LOGGED("meals", false),
    TRAINING_STREAK_WEEKS("weeks in a row hitting the weekly session target", false),
    MEAL_STREAK_DAYS("days in a row with a logged meal", false),
    PROTEIN_DAYS_IN_WEEK("days within one week reaching 1.6 g protein per kg (max 7)", false),
    WEIGHT_GAINED_KG("kg gained since the first weigh-in", false),
    WEIGHT_LOST_KG("kg lost since the first weigh-in", false),
    EXERCISE_SESSIONS("sessions of the exercise", true),
    LIFT_E1RM_KG("kg estimated one-rep max on the exercise", true),
    LIFT_BODYWEIGHT_RATIO("x body weight, estimated one-rep max on the exercise", true);

    private final String unit;
    private final boolean needsExercise;

    BadgeRule(String unit, boolean needsExercise) {
        this.unit = unit;
        this.needsExercise = needsExercise;
    }

    public String unit() { return unit; }
    public boolean needsExercise() { return needsExercise; }
}
