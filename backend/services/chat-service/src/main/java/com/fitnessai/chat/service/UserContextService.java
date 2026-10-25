package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.CoachPreferences;
import com.fitnessai.chat.dto.ChatDtos.FileRow;
import com.fitnessai.chat.dto.ChatDtos.HistoryData;
import com.fitnessai.chat.dto.ChatDtos.MealRow;
import com.fitnessai.chat.dto.ChatDtos.NutritionData;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.model.Conversation;
import com.fitnessai.chat.model.UserMemory;
import com.fitnessai.chat.repository.ConversationRepository;
import com.fitnessai.chat.repository.UserMemoryRepository;
import com.fitnessai.chat.repository.WeeklyReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Everything the app knows about the user, for the coach's prompt: profile,
 * preferences and health notes, training log, recent meals, goal history,
 * saved plans, the latest weekly report, long-term memories from every
 * conversation, and the other recent conversations.
 *
 * Gathering it means calls to three services, so it's cached in Redis and
 * rebuilt every {@value #REFRESH_EVERY} user messages (counted across all of
 * the user's conversations), when the cache is missing, or right after the
 * coach changed the user's data with a tool.
 */
@Service
public class UserContextService {

    public static final int REFRESH_EVERY = 4;
    static final int MAX_MEMORIES = 60;
    private static final int RECENT_MEAL_DAYS = 3;
    private static final int OTHER_CONVERSATIONS = 5;
    private static final Duration TTL = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(UserContextService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** profile and preferences stay structured (SystemPrompt formats them); the rest is ready-made text. */
    public record UserContext(ProfileSnapshot profile, CoachPreferences preferences, String userData) {
    }

    private final DownstreamClients clients;
    private final UserMemoryRepository memories;
    private final ConversationRepository conversations;
    private final WeeklyReportRepository reports;
    private final StringRedisTemplate redis;

    public UserContextService(DownstreamClients clients, UserMemoryRepository memories, ConversationRepository conversations,
                              WeeklyReportRepository reports, StringRedisTemplate redis) {
        this.clients = clients;
        this.memories = memories;
        this.conversations = conversations;
        this.reports = reports;
        this.redis = redis;
    }

    static boolean refreshDue(long userMessagesBefore) {
        return userMessagesBefore % REFRESH_EVERY == 0;
    }

    /** The cached snapshot, rebuilt when due. userMessagesBefore excludes the message being answered. */
    public UserContext get(long userId, String token, long userMessagesBefore) {
        if (!refreshDue(userMessagesBefore)) {
            UserContext cached = readCache(userId);
            if (cached != null) return cached;
        }
        UserContext fresh = build(userId, token);
        writeCache(userId, fresh);
        return fresh;
    }

    /** Forces a rebuild on the next message (e.g. the coach just logged a lift or saved a plan). */
    public void invalidate(long userId) {
        try {
            redis.delete(key(userId));
        } catch (Exception e) {
            log.debug("Redis unavailable for context invalidate: {}", e.getMessage());
        }
    }

    UserContext build(long userId, String token) {
        ProfileSnapshot profile = optional("profile", () -> clients.myProfile(token));
        CoachPreferences prefs = optional("preferences", () -> clients.myPreferences(token));
        String trainingLog = optional("training log", () -> clients.myProgressSummary(token));
        NutritionData nutrition = optional("meals", () -> clients.myNutrition(token));
        HistoryData history = optional("history", () -> clients.myHistory(token));
        List<FileRow> files = optional("files", () -> clients.myFiles(token));

        StringBuilder sb = new StringBuilder();
        section(sb, "TRAINING LOG", trainingLog == null || trainingLog.isBlank() ? "(training log unavailable)" : trainingLog.strip());
        section(sb, "RECENT MEALS (last " + RECENT_MEAL_DAYS + " days)", meals(nutrition));
        section(sb, "PROFILE HISTORY", history(history));
        section(sb, "SAVED PLANS AND FILES", files(files));
        section(sb, "LATEST WEEKLY REPORT", reports.findFirstByUserIdOrderByWeekStartDesc(userId)
                .map(r -> "Week of " + r.getWeekStart() + ": " + (r.getSummary() == null ? "" : r.getSummary().strip()))
                .orElse("(none yet)"));
        section(sb, "WHAT YOU REMEMBER ABOUT THE USER (from all conversations)", memoriesText(userId));
        section(sb, "THE USER'S OTHER RECENT CONVERSATIONS", conversationsText(userId));
        return new UserContext(profile, prefs, sb.toString().strip());
    }

    /** Saves new facts (case-insensitive de-duplication), keeping the newest {@value #MAX_MEMORIES}. */
    public int remember(long userId, List<String> facts) {
        List<UserMemory> existing = memories.findByUserIdOrderByCreatedAtAsc(userId);
        Set<String> known = new LinkedHashSet<>();
        existing.forEach(m -> known.add(norm(m.getFact())));
        int added = 0;
        for (String f : facts) {
            if (f == null || f.isBlank() || !known.add(norm(f))) continue;
            memories.save(new UserMemory(userId, f.strip().length() > 300 ? f.strip().substring(0, 300) : f.strip()));
            added++;
        }
        int overflow = existing.size() + added - MAX_MEMORIES;
        if (overflow > 0) memories.deleteAll(existing.subList(0, Math.min(overflow, existing.size())));
        if (added > 0) invalidate(userId);
        return added;
    }

    public void deleteForUser(long userId) {
        memories.deleteByUserId(userId);
        invalidate(userId);
    }

    private String memoriesText(long userId) {
        List<UserMemory> all = memories.findByUserIdOrderByCreatedAtAsc(userId);
        if (all.isEmpty()) return "(nothing yet)";
        StringBuilder sb = new StringBuilder();
        all.forEach(m -> sb.append("- ").append(m.getFact()).append('\n'));
        return sb.toString().strip();
    }

    private String conversationsText(long userId) {
        List<Conversation> recent = conversations.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, OTHER_CONVERSATIONS + 1));
        if (recent.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (Conversation c : recent) {
            String last = c.getLastMessage() == null ? "" : c.getLastMessage().replaceAll("\\s+", " ").strip();
            if (last.length() > 140) last = last.substring(0, 140) + "…";
            sb.append("- \"").append(c.getTitle()).append("\" (").append(c.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDate())
                    .append("), last message: ").append(last).append('\n');
        }
        return sb.toString().strip();
    }

    static String meals(NutritionData nutrition) {
        if (nutrition == null || nutrition.meals() == null || nutrition.meals().isEmpty()) return "(no meals logged)";
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(RECENT_MEAL_DAYS - 1);
        StringBuilder sb = new StringBuilder();
        for (MealRow m : nutrition.meals()) {
            if (m.date() == null || LocalDate.parse(m.date()).isBefore(from)) continue;
            sb.append(String.format(Locale.ROOT, "- %s: %s, %d kcal, %.0f g protein%n", m.date(), m.name(), m.calories(), m.proteinG()));
        }
        return sb.isEmpty() ? "(no meals in the last " + RECENT_MEAL_DAYS + " days)" : sb.toString().strip();
    }

    static String history(HistoryData h) {
        if (h == null) return "(unavailable)";
        List<String> lines = new ArrayList<>();
        if (h.since() != null) lines.add("- Using GymAI since " + h.since() + ".");
        if (h.startWeightKg() != null && h.currentWeightKg() != null) {
            lines.add(String.format(Locale.ROOT, "- Weight %.1f kg -> %.1f kg (%+.1f kg) since the first weigh-in.",
                    h.startWeightKg(), h.currentWeightKg(), h.weightChangeKg() == null ? 0 : h.weightChangeKg()));
        }
        if (h.events() != null) {
            h.events().stream()
                    .filter(e -> "goal".equals(e.type()) || "level".equals(e.type()))
                    .forEach(e -> lines.add("- " + e.date() + ": " + e.type() + (e.from() == null ? " set to " + e.to()
                            : " changed from " + e.from() + " to " + e.to())));
        }
        return lines.isEmpty() ? "(nothing yet)" : String.join("\n", lines);
    }

    static String files(List<FileRow> files) {
        if (files == null || files.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        files.stream().limit(25).forEach(f -> sb.append("- ").append(f.filename())
                .append(" (").append("generated".equalsIgnoreCase(f.type()) ? "created by you, the coach" : "uploaded by the user")
                .append(f.createdAt() == null ? "" : ", " + f.createdAt().substring(0, Math.min(10, f.createdAt().length())))
                .append(")\n"));
        return sb.toString().strip();
    }

    private static void section(StringBuilder sb, String title, String body) {
        sb.append("--- ").append(title).append(" ---\n").append(body).append("\n\n");
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static <T> T optional(String what, Supplier<T> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("{} lookup failed, continuing without it: {}", what, e.getMessage());
            return null;
        }
    }

    private static String key(long userId) {
        return "coach:context:" + userId;
    }

    private UserContext readCache(long userId) {
        try {
            String json = redis.opsForValue().get(key(userId));
            return json == null ? null : JSON.readValue(json, UserContext.class);
        } catch (Exception e) {
            log.debug("Context cache read failed, rebuilding: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(long userId, UserContext ctx) {
        try {
            redis.opsForValue().set(key(userId), JSON.writeValueAsString(ctx), TTL);
        } catch (Exception e) {
            log.debug("Context cache write failed: {}", e.getMessage());
        }
    }
}
