package com.fitnessai.knowledge.controller;

import com.fitnessai.knowledge.dto.ApiResponse;
import com.fitnessai.knowledge.dto.KnowledgeDtos;
import com.fitnessai.knowledge.dto.KnowledgeDtos.IndexRequest;
import com.fitnessai.knowledge.dto.KnowledgeDtos.IndexResult;
import com.fitnessai.knowledge.dto.KnowledgeDtos.MemoryRequest;
import com.fitnessai.knowledge.dto.KnowledgeDtos.MemoryResult;
import com.fitnessai.knowledge.dto.KnowledgeDtos.RetrieveRequest;
import com.fitnessai.knowledge.dto.KnowledgeDtos.RetrieveResponse;
import com.fitnessai.knowledge.security.Caller;
import com.fitnessai.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal API — called by chat-service and file-service, not routed by the gateway. */
@RestController
@RequestMapping("/internal/knowledge")
@Validated
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/documents")
    public ApiResponse<IndexResult> index(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody IndexRequest req) {
        return ApiResponse.ok(knowledge.index(Caller.from(jwt), req));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable @Pattern(regexp = KnowledgeDtos.DOCUMENT_ID_PATTERN) String documentId) {
        knowledge.deleteDocument(Caller.from(jwt), documentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/retrieve")
    public ApiResponse<RetrieveResponse> retrieve(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody RetrieveRequest req) {
        return ApiResponse.ok(knowledge.retrieve(Caller.from(jwt), req.query(), req.topK()));
    }

    @PostMapping("/memories")
    public ApiResponse<MemoryResult> remember(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MemoryRequest req) {
        return ApiResponse.ok(knowledge.remember(Caller.from(jwt), req.facts()));
    }
}
