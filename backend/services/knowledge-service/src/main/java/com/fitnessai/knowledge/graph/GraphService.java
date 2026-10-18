package com.fitnessai.knowledge.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

    // Bounds LLM cost per document: chunks are packed into ~4k-char batches,
    // at most MAX_BATCHES extraction calls per document.
    static final int BATCH_CHARS = 4000;
    static final int MAX_BATCHES = 25;
    private static final int FACT_LIMIT = 25;

    private final GraphExtractor extractor;
    private final GraphRepository repository;

    public GraphService(GraphExtractor extractor, GraphRepository repository) {
        this.extractor = extractor;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    void initSchema() {
        try {
            repository.ensureSchema();
        } catch (Exception e) {
            log.warn("Could not ensure Neo4j schema at startup (will retry implicitly on use): {}", e.getMessage());
        }
    }

    @Async("graphExecutor")
    public void indexDocumentAsync(String scope, String documentId, List<String> chunks) {
        String source = GraphKeys.source(scope, documentId);
        List<String> batches = batch(chunks);
        if (batches.size() > MAX_BATCHES) {
            log.info("Document {} has {} extraction batches; only the first {} go to the graph.",
                    documentId, batches.size(), MAX_BATCHES);
            batches = batches.subList(0, MAX_BATCHES);
        }
        int facts = 0;
        for (String text : batches) {
            try {
                List<Triple> triples = extractor.extract(text);
                repository.upsert(triples, source);
                facts += triples.size();
            } catch (Exception e) {
                log.warn("Graph extraction failed for a batch of {}: {}", documentId, e.getMessage());
            }
        }
        log.info("Graph extraction for {} finished: {} facts.", documentId, facts);
    }

    /** Graph context is a bonus on top of vector search — failures degrade to no facts, not an error. */
    public List<String> factsFor(String query, List<String> readableScopes) {
        try {
            return repository.factsFor(query, readableScopes, FACT_LIMIT);
        } catch (Exception e) {
            log.warn("Graph lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void removeDocument(String scope, String documentId) {
        repository.removeSource(GraphKeys.source(scope, documentId));
    }

    public void removeDocumentEverywhere(String documentId) {
        repository.removeDocumentEverywhere(documentId);
    }

    static List<String> batch(List<String> chunks) {
        List<String> batches = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk == null || chunk.isBlank()) {
                continue;
            }
            if (!current.isEmpty() && current.length() + chunk.length() > BATCH_CHARS) {
                batches.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(chunk);
        }
        if (!current.isEmpty()) {
            batches.add(current.toString());
        }
        return batches;
    }
}
