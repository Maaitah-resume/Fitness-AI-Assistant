package com.fitnessai.profile.controller;

import com.fitnessai.profile.service.HistoryService;
import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.dto.ProfileDtos.ProfileResponse;
import com.fitnessai.profile.dto.ProfileDtos.ProfileStats;
import com.fitnessai.profile.dto.ProfileDtos.ProfileUpdateRequest;
import com.fitnessai.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profiles;
    private final HistoryService historyService;

    public ProfileController(ProfileService profiles, HistoryService historyService) {
        this.profiles = profiles;
        this.historyService = historyService;
    }

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(profiles.get(userId(jwt)));
    }

    /** Weight since the start and a timeline of milestones, newest first. */
    @GetMapping("/me/history")
    public ApiResponse<HistoryService.History> history(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(historyService.forUser(userId(jwt)));
    }

    @PutMapping("/me")
    public ApiResponse<ProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody ProfileUpdateRequest req) {
        return ApiResponse.ok(profiles.upsert(userId(jwt), req));
    }

    @GetMapping("/admin/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProfileResponse> forUser(@PathVariable long userId) {
        return ApiResponse.ok(profiles.get(userId));
    }

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProfileStats> stats() {
        return ApiResponse.ok(profiles.stats());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, List<ProfileResponse>>> all() {
        return ApiResponse.ok(Map.of("profiles", profiles.all()));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
