package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.CoachPreferences;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptTest {

    @Test
    void preferencesShapeTheCoach() {
        String strict = SystemPrompt.build(null,
                new CoachPreferences("imperial", "detailed", "strict", 4, "dumbbells", "left knee"),
                "Body weight: 60.0 kg", RetrievedContext.EMPTY, false);

        assertThat(strict).contains("Answer length: detailed", "Tone: strict", "pounds (lb)",
                "exactly this many sessions", "dumbbells only", "left knee", "Body weight: 60.0 kg");
        assertThat(strict).doesNotContain("Reply in Modern Standard Arabic");
    }

    @Test
    void healthNotesReachTheCoach() {
        String prompt = SystemPrompt.build(null,
                new CoachPreferences("metric", "short", "friendly", null, null, null, "peanuts, shellfish",
                        java.util.List.of("halal", "lactose_free")),
                null, RetrievedContext.EMPTY, false);

        assertThat(prompt).contains("Food allergies and intolerances", "peanuts, shellfish",
                "Dietary style: halal (no pork or alcohol; halal meat only), lactose-free");
    }

    @Test
    void missingPreferencesFallBackToTheDefaultsAndArabicIsRequested() {
        String prompt = SystemPrompt.build(null, null, null, RetrievedContext.EMPTY, true);

        assertThat(prompt).contains("Answer length: short", "Tone: friendly", "Units: metric",
                "(training log unavailable)", "Reply in Modern Standard Arabic");
        assertThat(prompt).doesNotContain("Training days per week", "Equipment:", "Injuries");
    }
}
