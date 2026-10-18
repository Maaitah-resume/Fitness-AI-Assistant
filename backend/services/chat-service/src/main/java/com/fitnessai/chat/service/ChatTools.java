package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.DocSection;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProfileUpdate;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Tools the model may call during one chat turn. A fresh instance is built
 * per request around that request's bearer token, so a tool can only ever
 * act as the user who is chatting. Side effects are recorded on the
 * instance for the response (files created, profile changed).
 */
public class ChatTools {

    private final String token;
    private final DownstreamClients clients;
    private final List<GeneratedFile> generatedFiles = new ArrayList<>();
    private boolean profileUpdated;

    public ChatTools(String token, DownstreamClients clients) {
        this.token = token;
        this.clients = clients;
    }

    @Tool(description = """
            Save details the user has told you about themselves to their fitness profile.
            Pass only the fields the user explicitly stated in this conversation; leave the rest null.
            Convert units first (height to centimetres, weight to kilograms).""")
    public String updateFitnessProfile(
            @ToolParam(required = false, description = "Age in years (10-100)") Integer age,
            @ToolParam(required = false, description = "One of: male, female, other") String gender,
            @ToolParam(required = false, description = "Height in centimetres") Double heightCm,
            @ToolParam(required = false, description = "Weight in kilograms") Double weightKg,
            @ToolParam(required = false, description = "One of: muscle_gain, fat_loss, endurance, general_fitness")
            String goal,
            @ToolParam(required = false, description = "One of: beginner, intermediate, advanced") String level) {
        try {
            ProfileSnapshot saved = clients.updateProfile(token,
                    new ProfileUpdate(age, gender, heightCm, weightKg, goal, level));
            profileUpdated = true;
            return saved == null ? "Saved." : "Saved. Profile is now: " + saved.summary()
                    + (saved.complete() ? "" : " (still incomplete)");
        } catch (RestClientResponseException e) {
            return "The profile service rejected that (" + e.getResponseBodyAsString()
                    + "). Ask the user to clarify the invalid value.";
        } catch (Exception e) {
            return "The profile could not be saved right now; tell the user it will need to be retried.";
        }
    }

    @Tool(description = """
            Create a downloadable PDF for the user, e.g. a workout plan, meal plan or summary.
            Use only when the user asks for a document, file, PDF or something to download/print.
            Provide the complete final content — the document contains exactly what you pass.""")
    public String generateDocument(
            @ToolParam(description = "Document title") String title,
            @ToolParam(description = "Sections in order; body is plain text, use '- ' for bullet lines")
            List<DocSection> sections) {
        try {
            GeneratedFile file = clients.generateDocument(token, new GenerateRequest(title, sections));
            if (file == null) {
                return "Document generation returned nothing; tell the user it failed.";
            }
            generatedFiles.add(file);
            return "Created '" + file.filename() + "'. It is attached to your reply as a download link; "
                    + "don't paste a URL yourself.";
        } catch (Exception e) {
            return "Document generation failed; tell the user and offer the content inline instead.";
        }
    }

    public List<GeneratedFile> generatedFiles() {
        return List.copyOf(generatedFiles);
    }

    public boolean profileUpdated() {
        return profileUpdated;
    }
}
