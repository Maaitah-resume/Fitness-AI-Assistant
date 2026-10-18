package com.fitnessai.file.service;

import com.fitnessai.file.client.KnowledgeClient;
import com.fitnessai.file.dto.FileDtos.FileStats;
import com.fitnessai.file.dto.FileDtos.FileView;
import com.fitnessai.file.dto.FileDtos.GenerateRequest;
import com.fitnessai.file.model.FileRecord;
import com.fitnessai.file.model.FileRecord.Status;
import com.fitnessai.file.model.FileRecord.Type;
import com.fitnessai.file.pdf.PdfRenderer;
import com.fitnessai.file.pdf.PdfTextExtractor;
import com.fitnessai.file.pdf.TextChunker;
import com.fitnessai.file.repository.FileRecordRepository;
import com.fitnessai.file.security.Caller;
import com.fitnessai.file.storage.ObjectStorage;
import com.fitnessai.file.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final String PDF = "application/pdf";

    public record Download(String filename, byte[] content) {
    }

    private final FileRecordRepository files;
    private final ObjectStorage storage;
    private final PdfTextExtractor extractor;
    private final PdfRenderer renderer;
    private final KnowledgeClient knowledge;

    public FileService(FileRecordRepository files, ObjectStorage storage, PdfTextExtractor extractor,
                       PdfRenderer renderer, KnowledgeClient knowledge) {
        this.files = files;
        this.storage = storage;
        this.extractor = extractor;
        this.renderer = renderer;
        this.knowledge = knowledge;
    }

    /**
     * Store the PDF, extract and chunk its text, and index it in
     * knowledge-service under the uploader's identity. A record is kept even
     * when indexing fails, with the reason, so the UI can show what happened.
     */
    public FileView upload(Caller caller, String originalFilename, byte[] bytes, boolean shared) {
        if (shared && !caller.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can upload shared documents.");
        }
        if (bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (!PdfTextExtractor.looksLikePdf(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files are supported.");
        }

        String filename = sanitizeFilename(originalFilename, "document");
        FileRecord record = files.save(new FileRecord(caller.userId(), filename, Type.UPLOAD, Status.PROCESSING,
                shared, bytes.length, storageKey(caller.userId())));
        store(record, bytes);

        String text;
        try {
            text = extractor.extract(bytes);
        } catch (IOException e) {
            record.markFailed("Could not read the PDF: " + e.getMessage());
            return FileView.from(files.save(record));
        }
        List<String> chunks = TextChunker.chunk(text);
        if (chunks.isEmpty()) {
            record.markNoText();
            return FileView.from(files.save(record));
        }
        try {
            knowledge.index(caller.token(), record.knowledgeDocumentId(), filename, shared, chunks);
            record.markIndexed(chunks.size());
        } catch (Exception e) {
            log.warn("Indexing file {} failed: {}", record.getId(), e.getMessage());
            record.markFailed("Indexing failed — the document is stored but not searchable yet.");
        }
        return FileView.from(files.save(record));
    }

    public FileView generate(Caller caller, GenerateRequest req) {
        byte[] pdf;
        try {
            pdf = renderer.render(req.title(), req.sections().stream()
                    .map(s -> new PdfRenderer.Section(s.heading(), s.body()))
                    .toList());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not render the PDF.");
        }
        FileRecord record = files.save(new FileRecord(caller.userId(), sanitizeFilename(req.title(), "document"),
                Type.GENERATED, Status.READY, false, pdf.length, storageKey(caller.userId())));
        store(record, pdf);
        return FileView.from(record);
    }

    public List<FileView> mine(Caller caller) {
        return files.findByOwnerIdOrderByCreatedAtDesc(caller.userId()).stream().map(FileView::from).toList();
    }

    public List<FileView> all() {
        return files.findAllByOrderByCreatedAtDesc().stream().map(FileView::from).toList();
    }

    public Download download(Caller caller, long id) {
        FileRecord f = readable(caller, id);
        try {
            return new Download(f.getFilename(), storage.get(f.getStorageKey()));
        } catch (StorageException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable.");
        }
    }

    /**
     * Removes the search index entries first: if that fails we abort, rather
     * than delete the file while its content stays retrievable in chat.
     */
    public void delete(Caller caller, long id) {
        FileRecord f = files.findById(id)
                .filter(r -> caller.admin() || r.getOwnerId() == caller.userId())
                .orElseThrow(FileService::notFound);
        if (f.getType() == Type.UPLOAD && f.getStatus() == Status.INDEXED) {
            try {
                knowledge.delete(caller.token(), f.knowledgeDocumentId());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Couldn't remove the document from the search index; nothing was deleted. Try again.");
            }
        }
        deleteObjectQuietly(f);
        files.delete(f);
    }

    /** user-deleted event: knowledge-service clears its own data for the user. */
    public void deleteAllForUser(long userId) {
        List<FileRecord> owned = files.findByOwnerId(userId);
        owned.forEach(this::deleteObjectQuietly);
        files.deleteAll(owned);
    }

    public FileStats stats() {
        Map<String, Long> byType = new TreeMap<>();
        Map<String, Long> byStatus = new TreeMap<>();
        long total = 0;
        long bytes = 0;
        for (Object[] row : files.countsByTypeAndStatus()) {
            long count = (Long) row[2];
            byType.merge(((Enum<?>) row[0]).name().toLowerCase(Locale.ROOT), count, Long::sum);
            byStatus.merge(((Enum<?>) row[1]).name().toLowerCase(Locale.ROOT), count, Long::sum);
            total += count;
            bytes += ((Number) row[3]).longValue();
        }
        return new FileStats(total, bytes, byType, byStatus);
    }

    /** Owner, any admin, or anyone for shared uploads. 404 otherwise so ids can't be probed. */
    private FileRecord readable(Caller caller, long id) {
        return files.findById(id)
                .filter(f -> caller.admin() || f.getOwnerId() == caller.userId()
                        || (f.isShared() && f.getType() == Type.UPLOAD))
                .orElseThrow(FileService::notFound);
    }

    private void store(FileRecord record, byte[] bytes) {
        try {
            storage.put(record.getStorageKey(), bytes, PDF);
        } catch (StorageException e) {
            files.delete(record);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "File storage is unavailable.");
        }
    }

    private void deleteObjectQuietly(FileRecord f) {
        try {
            storage.delete(f.getStorageKey());
        } catch (StorageException e) {
            log.warn("Could not delete object {} (record {} removed anyway): {}", f.getStorageKey(), f.getId(),
                    e.getMessage());
        }
    }

    private static String storageKey(long ownerId) {
        return ownerId + "/" + UUID.randomUUID() + ".pdf";
    }

    /**
     * Keeps letters (any script, incl. Arabic and its diacritics), digits,
     * spaces, dot, underscore and hyphen; always ends in .pdf. Names are only
     * displayed and sent in Content-Disposition — storage keys are UUIDs.
     */
    static String sanitizeFilename(String name, String fallback) {
        String base = name == null ? "" : name.replaceAll("(?i)\\.pdf$", "");
        base = base.replaceAll("[^\\p{L}\\p{M}\\p{N} ._-]", "").replaceAll("\\s+", " ").strip();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.')) {
            base = fallback;
        }
        if (base.length() > 120) {
            base = base.substring(0, 120).strip();
        }
        return base + ".pdf";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found.");
    }
}
