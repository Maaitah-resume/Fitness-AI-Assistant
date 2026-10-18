package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.ChatAdminStats;
import com.fitnessai.chat.dto.ChatDtos.MyChatStats;
import com.fitnessai.chat.dto.ChatDtos.UserActivity;
import com.fitnessai.chat.model.ChatMessage.Role;
import com.fitnessai.chat.repository.ChatMessageRepository;
import com.fitnessai.chat.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class ChatStatsService {

    static final int DAYS = 30;

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;

    public ChatStatsService(ConversationRepository conversations, ChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public MyChatStats forUser(long userId) {
        return new MyChatStats(
                conversations.countByUserId(userId),
                messages.countByUserIdAndRole(userId, Role.USER),
                messages.lastActivity(userId),
                daily(messages.messageTimesSince(userId, Role.USER, windowStart())));
    }

    public ChatAdminStats global() {
        return new ChatAdminStats(
                conversations.count(),
                messages.countByRole(Role.USER),
                messages.countByRole(Role.ASSISTANT),
                messages.activeUsersSince(Instant.now().minus(7, ChronoUnit.DAYS)),
                daily(messages.allMessageTimesSince(Role.USER, windowStart())));
    }

    public List<UserActivity> activityByUser() {
        Map<Long, Long> conversationCounts = new HashMap<>();
        conversations.countsByUser().forEach(r -> conversationCounts.put((Long) r[0], (Long) r[1]));
        Map<Long, UserActivity> out = new HashMap<>();
        for (Object[] r : messages.activityByUser(Role.USER)) {
            long userId = (Long) r[0];
            out.put(userId, new UserActivity(userId, conversationCounts.getOrDefault(userId, 0L), (Long) r[1],
                    (Instant) r[2]));
        }
        // Conversations created but never messaged in.
        conversationCounts.forEach((userId, count) ->
                out.putIfAbsent(userId, new UserActivity(userId, count, 0, null)));
        return List.copyOf(out.values());
    }

    private static Instant windowStart() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(DAYS - 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Zero-filled per-day counts (UTC) for the last DAYS days, oldest first. */
    static Map<String, Long> daily(List<Instant> times) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, Long> buckets = new TreeMap<>();
        for (int i = DAYS - 1; i >= 0; i--) {
            buckets.put(today.minusDays(i).toString(), 0L);
        }
        times.forEach(t -> buckets.computeIfPresent(t.atZone(ZoneOffset.UTC).toLocalDate().toString(), (k, v) -> v + 1));
        return buckets;
    }
}
