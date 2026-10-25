package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.Citation;
import com.fitnessai.chat.dto.ChatDtos.DocSection;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.LiftLog;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProfileUpdate;
import com.fitnessai.chat.dto.ChatDtos.WeightLog;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatTools.class);

    private final String token;
    private final DownstreamClients clients;
    private static final int MAX_WEB_SEARCHES = 3;

    private final WebSearch web;
    private final List<GeneratedFile> generatedFiles = new ArrayList<>();
    private final List<Citation> webCitations = new ArrayList<>();
    private int webSearches;
    private boolean profileUpdated;
    private boolean progressLogged;

    public ChatTools(String token, DownstreamClients clients) {
        this(token, clients, null);
    }

    public ChatTools(String token, DownstreamClients clients, WebSearch web) {
        this.token = token;
        this.clients = clients;
        this.web = web;
    }

    @Tool(description = """
            Search the web for current or specific information the retrieved documents don't cover:
            recent research, official guidelines, supplement safety, product or event details.
            Returns findings with numbered sources; cite what you use with those [W#] markers.""")
    public String searchWeb(@ToolParam(description = "A focused search query, e.g. 'creatine safety teenagers research'")
                            String query) {
        if (web == null || !web.available()) {
            return "Web search isn't available right now; answer from general knowledge and say so.";
        }
        if (webSearches >= MAX_WEB_SEARCHES) {
            return "Search limit for this reply reached; answer with what you've found.";
        }
        webSearches++;
        try {
            WebSearch.Result result = web.search(query);
            if (result.sources().isEmpty() && result.findings().isBlank()) {
                return "The search found nothing useful; answer from general knowledge.";
            }
            StringBuilder sb = new StringBuilder("Findings:\n").append(result.findings()).append("\n\nSources:\n");
            for (WebSearch.Source s : result.sources()) {
                Citation c = webCitations.stream().filter(x -> x.url().equals(s.url())).findFirst().orElseGet(() -> {
                    Citation added = new Citation("W" + (webCitations.size() + 1), "web", s.title(), s.url());
                    webCitations.add(added);
                    return added;
                });
                sb.append('[').append(c.id()).append("] ").append(c.title()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            // A 429 here usually means the Gemini key has no Google Search grounding quota (free tier).
            log.warn("Web search failed: {}", e.getMessage());
            if (e instanceof RestClientResponseException r && r.getStatusCode().value() == 429) {
                return "Web search isn't available on the current AI plan (search quota exhausted). Tell the user "
                        + "briefly that live web search is unavailable right now, then answer from general knowledge.";
            }
            return "The web search failed; answer from general knowledge and mention you couldn't check online.";
        }
    }

    /** Web pages found during this turn, available for the reply to cite. */
    public List<Citation> webCitations() {
        return List.copyOf(webCitations);
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

    @Tool(description = """
            Log a body-weight reading the user reported. Convert to kilograms first.
            Leave date null for today; otherwise use YYYY-MM-DD.""")
    public String logBodyWeight(
            @ToolParam(description = "Body weight in kilograms (25-350)") Double weightKg,
            @ToolParam(required = false, description = "Date as YYYY-MM-DD, or null for today") String date) {
        try {
            clients.logWeight(token, new WeightLog(blankToNull(date), weightKg));
            progressLogged = true;
            profileUpdated = true; // the newest weigh-in also updates the profile weight
            return "Logged " + weightKg + " kg.";
        } catch (RestClientResponseException e) {
            return "The log rejected that (" + e.getResponseBodyAsString() + "). Ask the user to check the value.";
        } catch (Exception e) {
            return "The weigh-in could not be logged right now; tell the user to try again later.";
        }
    }

    @Tool(description = """
            Log a lift the user reported doing: exercise, weight and reps (sets optional).
            Convert to kilograms; use 0 kg for bodyweight exercises. Leave date null for today.""")
    public String logLift(
            @ToolParam(description = "Exercise name, e.g. Back squat") String exercise,
            @ToolParam(description = "Weight in kilograms (0 for bodyweight)") Double weightKg,
            @ToolParam(description = "Reps in the set (1-100)") Integer reps,
            @ToolParam(required = false, description = "Number of sets at this weight and reps") Integer sets,
            @ToolParam(required = false, description = "Date as YYYY-MM-DD, or null for today") String date) {
        try {
            boolean pr = clients.logLift(token, new LiftLog(blankToNull(date), exercise, weightKg, reps, sets));
            progressLogged = true;
            return "Logged " + exercise + ": " + weightKg + " kg x" + reps + (sets == null ? "" : " for " + sets + " sets") + "."
                    + (pr ? " This is a NEW PERSONAL RECORD for " + exercise + ": congratulate the user on it." : "");
        } catch (RestClientResponseException e) {
            return "The log rejected that (" + e.getResponseBodyAsString() + "). Ask the user to check the values.";
        } catch (Exception e) {
            return "The lift could not be logged right now; tell the user to try again later.";
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s) ? null : s;
    }

    public boolean progressLogged() {
        return progressLogged;
    }

    public List<GeneratedFile> generatedFiles() {
        return List.copyOf(generatedFiles);
    }

    public boolean profileUpdated() {
        return profileUpdated;
    }
}
