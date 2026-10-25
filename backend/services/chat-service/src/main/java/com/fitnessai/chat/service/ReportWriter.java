package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.DocSection;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/** Turns a week's numbers into a short coaching report. Thin seam around the LLM so it can be mocked. */
@Service
public class ReportWriter {

    public record ReportDraft(String summary, List<DocSection> sections) {
    }

    private static final String INSTRUCTIONS = """
            You are GymAI, writing the user's weekly progress report from the WEEK DATA below.
            - Use only numbers that appear in the data. Never invent workouts, weights or meals.
            - summary: 1-2 sentences with the most important takeaway of the week.
            - sections, in this order: Highlights, Training, Body weight, Nutrition, Focus for next week.
              Each body is plain text; use "- " at the start of bullet lines. Keep each section short.
            - Compare training sessions with the planned days per week when a plan is given, and say
              plainly if sessions were missed. Relate body-weight change to the user's goal.
            - Protein: compare the average with the target in the data when both are present.
            - If an area has no data this week, say so in one line and suggest logging it.
            - Focus for next week: 2-3 concrete, safe actions.
            """;

    private final ChatClient chat;

    public ReportWriter(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public ReportDraft write(String weekData, boolean arabic) {
        String language = arabic
                ? "Write the summary and all section headings and bodies in Modern Standard Arabic."
                : "Write in English.";
        return chat.prompt()
                .system(INSTRUCTIONS + "\n" + language)
                .user("WEEK DATA:\n" + weekData)
                .call()
                .entity(ReportDraft.class);
    }
}
