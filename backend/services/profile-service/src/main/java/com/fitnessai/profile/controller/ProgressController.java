package com.fitnessai.profile.controller;

import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.dto.ProgressDtos.LiftRequest;
import com.fitnessai.profile.dto.ProgressDtos.LiftView;
import com.fitnessai.profile.dto.ProgressDtos.ProgressSummary;
import com.fitnessai.profile.dto.ProgressDtos.ProgressView;
import com.fitnessai.profile.dto.ProgressDtos.WeightRequest;
import com.fitnessai.profile.dto.ProgressDtos.WeightView;
import com.fitnessai.profile.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own training log: body weight and lifts. */
@RestController
@RequestMapping("/api/v1/profile/progress/me")
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    @GetMapping
    public ApiResponse<ProgressView> view(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(progress.view(userId(jwt)));
    }

    /** Compact text for chat-service's system prompt. */
    @GetMapping("/summary")
    public ApiResponse<ProgressSummary> summary(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(new ProgressSummary(progress.summary(userId(jwt))));
    }

    @PostMapping("/weight")
    public ApiResponse<WeightView> logWeight(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WeightRequest req) {
        return ApiResponse.ok(progress.logWeight(userId(jwt), req));
    }

    @PostMapping("/lifts")
    public ApiResponse<LiftView> logLift(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LiftRequest req) {
        return ApiResponse.ok(progress.logLift(userId(jwt), req));
    }

    @DeleteMapping("/weight/{id}")
    public ApiResponse<Void> deleteWeight(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        progress.deleteWeight(userId(jwt), id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/lifts/{id}")
    public ApiResponse<Void> deleteLift(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        progress.deleteLift(userId(jwt), id);
        return ApiResponse.ok(null);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
