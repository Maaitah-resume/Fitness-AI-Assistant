package com.fitnessai.chat.controller;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.model.PromptVersion;
import com.fitnessai.chat.service.PromptService;
import com.fitnessai.chat.service.SystemPrompt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Admin editor for the coach's base instructions, with version history. */
@RestController
@RequestMapping("/api/v1/chat/admin/prompt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromptController {

    public record PromptUpdate(@NotBlank @Size(min = 50, max = 8000) String text) {
    }

    public record VersionView(Long id, String action, Long createdBy, String createdByName, Instant createdAt,
                              int characters, String preview) {
        static VersionView from(PromptVersion v) {
            String flat = v.getText().replaceAll("\\s+", " ").strip();
            return new VersionView(v.getId(), v.getAction(), v.getCreatedBy(), v.getCreatedByName(), v.getCreatedAt(),
                    v.getText().length(), flat.length() > 120 ? flat.substring(0, 120) + "…" : flat);
        }
    }

    /** text is what the coach uses now; appended explains the parts admins can't edit. */
    public record PromptState(String text, boolean isDefault, String defaultText, VersionView current,
                              List<VersionView> history, List<String> appended) {
    }

    private final PromptService prompts;

    public AdminPromptController(PromptService prompts) {
        this.prompts = prompts;
    }

    @GetMapping
    public ApiResponse<PromptState> get() {
        return ApiResponse.ok(state());
    }

    @GetMapping("/versions/{id}")
    public ApiResponse<String> version(@PathVariable long id) {
        return ApiResponse.ok(prompts.version(id).getText());
    }

    @PutMapping
    public ApiResponse<PromptState> save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PromptUpdate req) {
        prompts.save(req.text(), adminId(jwt), jwt.getClaimAsString("username"));
        return ApiResponse.ok(state());
    }

    @PostMapping("/reset")
    public ApiResponse<PromptState> reset(@AuthenticationPrincipal Jwt jwt) {
        prompts.reset(adminId(jwt), jwt.getClaimAsString("username"));
        return ApiResponse.ok(state());
    }

    @PostMapping("/versions/{id}/restore")
    public ApiResponse<PromptState> restore(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        prompts.restore(id, adminId(jwt), jwt.getClaimAsString("username"));
        return ApiResponse.ok(state());
    }

    private PromptState state() {
        String text = prompts.activeBase();
        return new PromptState(text, text.equals(SystemPrompt.DEFAULT_BASE), SystemPrompt.DEFAULT_BASE,
                prompts.current().map(VersionView::from).orElse(null),
                prompts.history().stream().map(VersionView::from).toList(),
                SystemPrompt.APPENDED_SECTIONS);
    }

    private static long adminId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
