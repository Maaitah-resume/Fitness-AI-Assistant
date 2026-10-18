package com.fitnessai.file.controller;

import com.fitnessai.file.dto.ApiResponse;
import com.fitnessai.file.dto.FileDtos.FileStats;
import com.fitnessai.file.dto.FileDtos.FileView;
import com.fitnessai.file.dto.FileDtos.GenerateRequest;
import com.fitnessai.file.security.Caller;
import com.fitnessai.file.service.FileService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> upload(@AuthenticationPrincipal Jwt jwt,
                                        @RequestPart("file") MultipartFile file,
                                        @RequestParam(defaultValue = "false") boolean shared) throws IOException {
        FileView view = files.upload(Caller.from(jwt), file.getOriginalFilename(), file.getBytes(), shared);
        String message = switch (view.status()) {
            case "indexed" -> "'" + view.filename() + "' uploaded and indexed (" + view.chunkCount() + " chunks).";
            case "no_text" -> "'" + view.filename() + "' was stored, but contains no readable text to search.";
            default -> "'" + view.filename() + "' was stored, but indexing failed: " + view.error();
        };
        return ApiResponse.ok(message, view);
    }

    @PostMapping("/generate")
    public ApiResponse<FileView> generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GenerateRequest req) {
        return ApiResponse.ok(files.generate(Caller.from(jwt), req));
    }

    @GetMapping({"", "/"})
    public ApiResponse<Map<String, List<FileView>>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(Map.of("files", files.mine(Caller.from(jwt))));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        FileService.Download d = files.download(Caller.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(d.filename()))
                .header("X-Content-Type-Options", "nosniff")
                .body(d.content());
    }

    /** Plain filename="..." for ASCII names; RFC 5987 filename*=UTF-8''... when e.g. Arabic. */
    static String contentDisposition(String filename) {
        boolean ascii = filename.chars().allMatch(c -> c < 128);
        ContentDisposition.Builder b = ContentDisposition.attachment();
        return (ascii ? b.filename(filename) : b.filename(filename, StandardCharsets.UTF_8)).build().toString();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        files.delete(Caller.from(jwt), id);
        return ApiResponse.ok("File deleted.", null);
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, List<FileView>>> all() {
        return ApiResponse.ok(Map.of("files", files.all()));
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FileStats> stats() {
        return ApiResponse.ok(files.stats());
    }
}
