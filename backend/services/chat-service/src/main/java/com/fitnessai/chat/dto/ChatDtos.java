package com.fitnessai.chat.dto;

import com.fitnessai.chat.model.ChatMessage;
import com.fitnessai.chat.model.Conversation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * snake_case component names are deliberate: they match the JSON contract
 * the existing React frontend already reads (chat_id, last_message, ...).
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record SendRequest(@NotBlank @Size(max = 8000) String message, Long chat_id) {
    }

    public record SendResponse(
            String response,
            Long chat_id,
            List<GeneratedFile> files,
            boolean profile_updated,
            List<String> sources,
            boolean degraded) {
    }

    public record ConversationView(Long id, String title, String last_message, Instant created_at, Instant updated_at) {
        public static ConversationView from(Conversation c) {
            return new ConversationView(c.getId(), c.getTitle(), c.getLastMessage(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(Long id, String role, String message, Instant created_at) {
        public static MessageView from(ChatMessage m) {
            return new MessageView(m.getId(), m.getRole().name().toLowerCase(Locale.ROOT), m.getContent(), m.getCreatedAt());
        }
    }

    public record MyChatStats(long conversations, long messagesSent, Instant lastActive, Map<String, Long> messagesByDay) {
    }

    public record UserActivity(long userId, long conversations, long messagesSent, Instant lastActive) {
    }

    public record ChatAdminStats(
            long totalConversations,
            long totalUserMessages,
            long totalAssistantMessages,
            long activeUsersLast7Days,
            Map<String, Long> messagesByDay) {
    }

    // ── Downstream payloads ──────────────────────────────────────────────────

    public record GeneratedFile(Long id, String filename, String downloadUrl) {
    }

    public record DocSection(String heading, String body) {
    }

    public record GenerateRequest(String title, List<DocSection> sections) {
    }

    public record RetrieveRequest(String query, Integer topK) {
    }

    public record RetrievedChunk(String text, String filename, String documentId, Double score) {
    }

    public record RetrievedContext(String context, List<RetrievedChunk> chunks, List<String> graphFacts,
                                   List<String> memories) {
        public static final RetrievedContext EMPTY = new RetrievedContext("", List.of(), List.of(), List.of());
    }

    public record MemoryRequest(List<String> facts) {
    }

    public record ProfileUpdate(Integer age, String gender, Double heightCm, Double weightKg, String goal,
                                String level) {
    }

    public record ProfileSnapshot(Integer age, String gender, Double heightCm, Double weightKg, Double bmi,
                                  String goal, String level, boolean complete) {

        /** Compact line for the system prompt; null fields omitted. */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            append(sb, "age", age);
            append(sb, "gender", gender);
            append(sb, "height_cm", heightCm);
            append(sb, "weight_kg", weightKg);
            append(sb, "bmi", bmi);
            append(sb, "goal", goal);
            append(sb, "level", level);
            return sb.isEmpty() ? "(no profile details saved yet)" : sb.toString();
        }

        private static void append(StringBuilder sb, String k, Object v) {
            if (v != null) {
                sb.append(sb.isEmpty() ? "" : ", ").append(k).append('=').append(v);
            }
        }
    }
}
