package com.fitnessai.knowledge.graph;

import java.util.List;
import java.util.Locale;

/** One extracted fact, e.g. (Squat, Exercise) -TARGETS-> (Quadriceps, MuscleGroup). */
public record Triple(String subject, String subjectType, String relation, String object, String objectType) {

    static final List<String> TYPES = List.of(
            "Exercise", "MuscleGroup", "Equipment", "Goal", "Nutrient", "Food", "Condition", "Program", "Other");

    static final List<String> RELATIONS = List.of(
            "TARGETS", "REQUIRES", "IMPROVES", "PART_OF", "CONTAINS", "RECOMMENDED_FOR",
            "CONTRAINDICATED_FOR", "ALTERNATIVE_TO", "RELATED_TO");

    public boolean isValid() {
        return GraphKeys.key(subject).length() >= 2 && GraphKeys.key(object).length() >= 2
                && !GraphKeys.key(subject).equals(GraphKeys.key(object))
                && subject.length() <= 80 && object.length() <= 80;
    }

    /** Clamp LLM output to the known vocabulary so the graph stays queryable. */
    public String normalizedRelation() {
        String r = relation == null ? "" : relation.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", "_");
        return RELATIONS.contains(r) ? r : "RELATED_TO";
    }

    public static String normalizedType(String type) {
        if (type == null) {
            return "Other";
        }
        return TYPES.stream().filter(t -> t.equalsIgnoreCase(type.trim())).findFirst().orElse("Other");
    }
}
