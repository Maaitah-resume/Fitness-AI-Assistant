package com.fitnessai.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class KnowledgeDtos {

    // documentId ends up inside vector-store filter expressions and graph
    // source tags, so it's restricted to a safe character set.
    public static final String DOCUMENT_ID_PATTERN = "[A-Za-z0-9_-]{1,64}";

    private KnowledgeDtos() {
    }

    public record IndexRequest(
            @NotBlank @Pattern(regexp = DOCUMENT_ID_PATTERN) String documentId,
            @NotBlank @Size(max = 255) String filename,
            boolean shared,
            @NotEmpty @Size(max = 5000) List<String> chunks) {
    }

    public record IndexResult(String documentId, int chunksIndexed, String scope, String graphExtraction) {
    }

    public record RetrieveRequest(
            @NotBlank @Size(max = 4000) String query,
            @Min(1) @Max(20) Integer topK) {
    }

    public record ChunkHit(String text, String filename, String documentId, Double score) {
    }

    public record RetrieveResponse(
            String context,
            List<ChunkHit> chunks,
            List<String> graphFacts,
            List<String> memories) {
    }

    public record MemoryRequest(@NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 500) String> facts) {
    }

    public record MemoryResult(int stored, int skippedAsDuplicate) {
    }
}
