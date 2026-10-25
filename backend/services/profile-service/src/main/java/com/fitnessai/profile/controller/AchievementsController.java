package com.fitnessai.profile.controller;

import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.service.AchievementsService;
import com.fitnessai.profile.service.AchievementsService.Achievements;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's streaks, personal records and badges. */
@RestController
@RequestMapping("/api/v1/profile/achievements/me")
public class AchievementsController {

    private final AchievementsService achievements;

    public AchievementsController(AchievementsService achievements) {
        this.achievements = achievements;
    }

    @GetMapping
    public ApiResponse<Achievements> get(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(achievements.forUser(Long.parseLong(jwt.getSubject())));
    }
}
