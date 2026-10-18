package com.fitnessai.knowledge.service;

import com.fitnessai.knowledge.dto.KnowledgeDtos.ChunkHit;
import com.fitnessai.knowledge.dto.KnowledgeDtos.IndexRequest;
import com.fitnessai.knowledge.dto.KnowledgeDtos.IndexResult;
import com.fitnessai.knowledge.dto.KnowledgeDtos.MemoryResult;
import com.fitnessai.knowledge.dto.KnowledgeDtos.RetrieveResponse;
import com.fitnessai.knowledge.graph.GraphService;
import com.fitnessai.knowledge.security.Caller;
import com.fitnessai.knowledge.vector.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int DEFAULT_TOP_K = 4;
    private static final int MEMORY_TOP_K = 3;
    private static final double MEMORY_RELEVANCE = 0.3;

    private final VectorIndexService vectors;
    private final GraphService graph;

    public KnowledgeService(VectorIndexService vectors, GraphService graph) {
        this.vectors = vectors;
        this.graph = graph;
    }

    public IndexResult index(Caller caller, IndexRequest req) {
        if (req.shared() && !caller.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can index shared documents.");
        }
        String scope = req.shared() ? Caller.SHARED_SCOPE : caller.ownScope();
        int indexed = vectors.indexDocument(req.documentId(), scope, req.filename(), req.chunks());
        graph.indexDocumentAsync(scope, req.documentId(), req.chunks());
        return new IndexResult(req.documentId(), indexed, scope, "queued");
    }

    /** Non-admins can only remove their own documents; admins remove a document wherever it lives. */
    public void deleteDocument(Caller caller, String documentId) {
        if (caller.admin()) {
            vectors.deleteDocument(documentId, null);
            graph.removeDocumentEverywhere(documentId);
        } else {
            vectors.deleteDocument(documentId, caller.ownScope());
            graph.removeDocument(caller.ownScope(), documentId);
        }
    }

    public RetrieveResponse retrieve(Caller caller, String query, Integer topK) {
        int k = topK == null ? DEFAULT_TOP_K : topK;

        List<ChunkHit> chunks = safely("document search", () -> vectors.searchDocuments(query, caller.readableScopes(), k))
                .stream()
                .map(d -> new ChunkHit(d.getText(), str(d, "filename"), str(d, "documentId"), d.getScore()))
                .toList();
        List<String> memories = safely("memory search",
                () -> vectors.searchMemories(query, caller.ownScope(), MEMORY_TOP_K, MEMORY_RELEVANCE))
                .stream().map(Document::getText).toList();
        List<String> facts = graph.factsFor(query, caller.readableScopes());

        return new RetrieveResponse(formatContext(chunks, facts, memories), chunks, facts, memories);
    }

    public MemoryResult remember(Caller caller, List<String> facts) {
        int stored = vectors.addMemories(caller.ownScope(), facts);
        return new MemoryResult(stored, facts.size() - stored);
    }

    static String formatContext(List<ChunkHit> chunks, List<String> facts, List<String> memories) {
        StringBuilder sb = new StringBuilder();
        if (!chunks.isEmpty()) {
            sb.append("[Document excerpts]\n");
            for (int i = 0; i < chunks.size(); i++) {
                ChunkHit c = chunks.get(i);
                sb.append('(').append(i + 1).append(") ")
                        .append(c.filename() == null ? "document" : c.filename()).append(": ")
                        .append(c.text().strip()).append("\n");
            }
        }
        if (!facts.isEmpty()) {
            sb.append(sb.isEmpty() ? "" : "\n").append("[Knowledge graph facts]\n");
            facts.forEach(f -> sb.append("- ").append(f).append("\n"));
        }
        if (!memories.isEmpty()) {
            sb.append(sb.isEmpty() ? "" : "\n").append("[What we know about this user]\n");
            memories.forEach(m -> sb.append("- ").append(m).append("\n"));
        }
        return sb.toString().strip();
    }

    // A failing embedding/vector call shouldn't take the whole retrieval down —
    // the chat can still answer from graph facts or general knowledge.
    private static List<Document> safely(String what, java.util.function.Supplier<List<Document>> call) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("{} failed: {}", what, e.getMessage());
            return List.of();
        }
    }

    private static String str(Document d, String key) {
        Object v = d.getMetadata().get(key);
        return v == null ? null : v.toString();
    }
}
