package com.fitnessai.knowledge.graph;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** LLM pass that turns a block of document text into (subject, relation, object) triples. */
@Component
public class GraphExtractor {

    private static final int MAX_TRIPLES_PER_CALL = 60;

    private static final String SYSTEM_PROMPT = """
            You extract a knowledge graph from fitness, training and nutrition text.
            Return only facts explicitly stated in the text — never infer or add general knowledge.
            Entity types: %s.
            Relations: %s.
            Use short canonical entity names (e.g. "Squat", "Quadriceps", "Barbell", "Protein").
            If the text has no relevant facts, return an empty list.
            """.formatted(String.join(", ", Triple.TYPES), String.join(", ", Triple.RELATIONS));

    record ExtractionResult(List<Triple> triples) {
    }

    private final ChatClient chat;

    public GraphExtractor(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    public List<Triple> extract(String text) {
        ExtractionResult result = chat.prompt()
                .system(SYSTEM_PROMPT)
                .user(text)
                .call()
                .entity(ExtractionResult.class);
        if (result == null || result.triples() == null) {
            return List.of();
        }
        return result.triples().stream().filter(Triple::isValid).limit(MAX_TRIPLES_PER_CALL).toList();
    }
}
