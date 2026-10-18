package com.fitnessai.knowledge.vector;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dense-vector side of retrieval over a single Qdrant collection holding two
 * kinds of points, told apart by the "kind" payload: document chunks and
 * per-user long-term memories. Every filter value interpolated below is
 * either generated here (scopes, kinds) or pattern-validated at the API
 * boundary (documentId), so filter expressions can't be injected.
 */
@Service
public class VectorIndexService {

    static final String KIND_DOCUMENT = "document";
    static final String KIND_MEMORY = "memory";
    private static final int EMBED_BATCH = 64;
    private static final double MEMORY_DUPLICATE_THRESHOLD = 0.92;

    private final VectorStore store;

    public VectorIndexService(VectorStore store) {
        this.store = store;
    }

    public int indexDocument(String documentId, String scope, String filename, List<String> chunks) {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            docs.add(new Document(text, Map.of(
                    "kind", KIND_DOCUMENT,
                    "documentId", documentId,
                    "scope", scope,
                    "filename", filename,
                    "chunkIndex", i)));
        }
        for (int start = 0; start < docs.size(); start += EMBED_BATCH) {
            store.add(docs.subList(start, Math.min(start + EMBED_BATCH, docs.size())));
        }
        return docs.size();
    }

    public List<Document> searchDocuments(String query, List<String> readableScopes, int topK) {
        return store.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.25)
                .filterExpression("kind == '" + KIND_DOCUMENT + "' && scope in [" + quoted(readableScopes) + "]")
                .build());
    }

    public void deleteDocument(String documentId, String scopeOrNullForAny) {
        String filter = "kind == '" + KIND_DOCUMENT + "' && documentId == '" + documentId + "'";
        if (scopeOrNullForAny != null) {
            filter += " && scope == '" + scopeOrNullForAny + "'";
        }
        store.delete(filter);
    }

    /** Every point (documents and memories) in one scope — used when a user is deleted. */
    public void deleteScope(String scope) {
        store.delete("scope == '" + scope + "'");
    }

    /** Stores facts about a user, skipping ones that near-duplicate an existing memory. Returns stored count. */
    public int addMemories(String scope, List<String> facts) {
        List<Document> fresh = new ArrayList<>();
        for (String fact : facts) {
            if (searchMemories(fact, scope, 1, MEMORY_DUPLICATE_THRESHOLD).isEmpty()) {
                fresh.add(new Document(fact.trim(), Map.of("kind", KIND_MEMORY, "scope", scope)));
            }
        }
        if (!fresh.isEmpty()) {
            store.add(fresh);
        }
        return fresh.size();
    }

    public List<Document> searchMemories(String query, String scope, int topK, double threshold) {
        return store.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .filterExpression("kind == '" + KIND_MEMORY + "' && scope == '" + scope + "'")
                .build());
    }

    private static String quoted(List<String> values) {
        return values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
    }
}
