package com.fitnessai.chat.memory;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Periodically distils durable facts about the user ("has a knee injury",
 * "trains at home") from recent turns and stores them in knowledge-service,
 * where retrieval surfaces them in later conversations.
 */
@Component
public class LongTermMemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryExtractor.class);

    /** Run extraction after every Nth user message in a conversation. */
    public static final int EVERY_N_USER_MESSAGES = 6;

    private static final String PROMPT = """
            From this conversation between a user and a fitness assistant, list durable facts about
            the USER that would help personalise future advice: injuries or medical limitations,
            equipment and training environment, schedule, dietary preferences or restrictions,
            preferences and dislikes, and stated long-term goals.
            Rules: only facts the user stated about themselves; one short third-person sentence each
            (e.g. "Has a lower-back injury."); skip anything already captured by age, gender, height,
            weight, goal or fitness level; skip transient details. Return an empty list if none.
            """;

    record Facts(List<String> facts) {
    }

    private final ChatClient chat;
    private final DownstreamClients clients;

    public LongTermMemoryExtractor(ChatClient.Builder builder, DownstreamClients clients) {
        this.chat = builder.build();
        this.clients = clients;
    }

    public static boolean isDue(long userMessagesInConversation) {
        return userMessagesInConversation > 0 && userMessagesInConversation % EVERY_N_USER_MESSAGES == 0;
    }

    @Async
    public void extractAndStore(String token, List<ShortTermMemory.Turn> turns) {
        try {
            String transcript = turns.stream()
                    .map(t -> (t.role() == ChatMessage.Role.USER ? "User: " : "Assistant: ") + t.content())
                    .collect(Collectors.joining("\n"));
            Facts facts = chat.prompt().system(PROMPT).user(transcript).call().entity(Facts.class);
            if (facts != null && facts.facts() != null && !facts.facts().isEmpty()) {
                List<String> clean = facts.facts().stream()
                        .filter(f -> f != null && !f.isBlank())
                        .map(String::strip)
                        .limit(20)
                        .toList();
                if (!clean.isEmpty()) {
                    clients.remember(token, clean);
                    log.info("Stored {} long-term memory facts", clean.size());
                }
            }
        } catch (Exception e) {
            log.warn("Long-term memory extraction skipped: {}", e.getMessage());
        }
    }
}
