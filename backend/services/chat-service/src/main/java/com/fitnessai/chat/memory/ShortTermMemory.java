package com.fitnessai.chat.memory;

import com.fitnessai.chat.model.ChatMessage;
import com.fitnessai.chat.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The active conversation window, cached in Redis as a capped list so hot
 * conversations don't hit Postgres every turn. Postgres stays the source of
 * truth: a cache miss (or Redis being down) rebuilds from chat_messages.
 * Entries are "U:<text>" / "A:<text>" — no serializer needed.
 */
@Component
public class ShortTermMemory {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemory.class);
    public static final int WINDOW = 20;
    private static final Duration TTL = Duration.ofHours(2);

    public record Turn(ChatMessage.Role role, String content) {
    }

    private final StringRedisTemplate redis;
    private final ChatMessageRepository messages;

    public ShortTermMemory(StringRedisTemplate redis, ChatMessageRepository messages) {
        this.redis = redis;
        this.messages = messages;
    }

    public List<Turn> window(long conversationId) {
        try {
            List<String> cached = redis.opsForList().range(key(conversationId), 0, -1);
            if (cached != null && !cached.isEmpty()) {
                return cached.stream().map(ShortTermMemory::decode).toList();
            }
        } catch (Exception e) {
            log.debug("Redis unavailable for window read, using database: {}", e.getMessage());
        }
        List<Turn> fromDb = new ArrayList<>(messages
                .findByConversationIdOrderByIdDesc(conversationId, PageRequest.of(0, WINDOW)).stream()
                .map(m -> new Turn(m.getRole(), m.getContent()))
                .toList()).reversed();
        cache(conversationId, fromDb);
        return fromDb;
    }

    public void append(long conversationId, List<Turn> turns) {
        try {
            String key = key(conversationId);
            redis.opsForList().rightPushAll(key, turns.stream().map(ShortTermMemory::encode).toList());
            redis.opsForList().trim(key, -WINDOW, -1);
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.debug("Redis unavailable for window write: {}", e.getMessage());
        }
    }

    public void clear(Collection<Long> conversationIds) {
        try {
            redis.delete(conversationIds.stream().map(ShortTermMemory::key).toList());
        } catch (Exception e) {
            log.debug("Redis unavailable for window clear: {}", e.getMessage());
        }
    }

    private void cache(long conversationId, List<Turn> turns) {
        if (!turns.isEmpty()) {
            append(conversationId, turns);
        }
    }

    static String key(long conversationId) {
        return "chat:" + conversationId + ":window";
    }

    static String encode(Turn t) {
        return (t.role() == ChatMessage.Role.USER ? "U:" : "A:") + t.content();
    }

    static Turn decode(String s) {
        return new Turn(s.startsWith("U:") ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT, s.substring(2));
    }
}
