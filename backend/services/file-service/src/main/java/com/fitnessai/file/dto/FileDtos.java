package com.fitnessai.file.dto;

import com.fitnessai.file.model.FileRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FileDtos {

    private FileDtos() {
    }

    public record SectionDto(@Size(max = 300) String heading, @Size(max = 20000) String body) {
    }

    public record GenerateRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull @Size(max = 100) List<@Valid SectionDto> sections) {
    }

    public record FileView(
            Long id,
            String filename,
            String type,
            String status,
            boolean shared,
            long sizeBytes,
            Integer chunkCount,
            String error,
            Instant createdAt,
            String downloadUrl) {

        public static FileView from(FileRecord f) {
            return new FileView(f.getId(), f.getFilename(), lower(f.getType()), lower(f.getStatus()), f.isShared(),
                    f.getSizeBytes(), f.getChunkCount(), f.getError(), f.getCreatedAt(),
                    "/api/v1/files/" + f.getId() + "/download");
        }

        private static String lower(Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
    }

    public record FileStats(long totalFiles, long totalBytes, Map<String, Long> byType, Map<String, Long> byStatus) {
    }

    // knowledge-service payload
    public record IndexRequest(String documentId, String filename, boolean shared, List<String> chunks) {
    }
}
