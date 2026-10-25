package com.fitnessai.chat.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.List;

/** Multimodal meal estimate: one photo in, structured nutrition numbers out. Thin seam so it can be mocked. */
@Service
public class MealVision {

    /** What the model returns; the user reviews and edits it before anything is logged. */
    public record MealEstimate(boolean isFood, String name, List<String> items, int calories, double proteinG,
                               double carbsG, double fatG, String confidence, String notes) {
    }

    private static final String INSTRUCTIONS = """
            You are a sports nutritionist estimating a meal from one photo.
            - Set isFood to false (and numbers to 0) if the photo does not show food or drink.
            - Otherwise identify the dish and its visible components with estimated portion sizes
              (e.g. "grilled chicken breast, about 150 g"), then estimate the whole plate's calories and
              protein, carbs and fat in grams.
            - Use typical home or restaurant portions when size is unclear, and say so in notes.
            - confidence is "low", "medium" or "high" depending on how clearly the portions are visible.
            - name is a short dish name (max 6 words). notes is one short sentence.
            """;

    private final ChatClient chat;

    public MealVision(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public MealEstimate estimate(byte[] image, MimeType type, boolean arabic) {
        String language = arabic
                ? "Write name, items and notes in Modern Standard Arabic."
                : "Write name, items and notes in English.";
        return chat.prompt()
                .system(INSTRUCTIONS + "\n" + language)
                .user(u -> u.text("Estimate the nutrition of this meal.").media(type, new ByteArrayResource(image)))
                .call()
                .entity(MealEstimate.class);
    }
}
