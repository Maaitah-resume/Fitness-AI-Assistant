package com.fitnessai.profile.controller;

import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.dto.PreferencesDtos.PreferencesUpdate;
import com.fitnessai.profile.dto.PreferencesDtos.PreferencesView;
import com.fitnessai.profile.model.Preferences;
import com.fitnessai.profile.repository.PreferencesRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's settings. Unsaved users get the defaults (metric, short, friendly). */
@RestController
@RequestMapping("/api/v1/profile/preferences/me")
public class PreferencesController {

    private final PreferencesRepository preferences;

    public PreferencesController(PreferencesRepository preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public ApiResponse<PreferencesView> get(@AuthenticationPrincipal Jwt jwt) {
        long userId = userId(jwt);
        return ApiResponse.ok(PreferencesView.from(preferences.findById(userId).orElseGet(() -> new Preferences(userId))));
    }

    @PutMapping
    @Transactional
    public ApiResponse<PreferencesView> update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesUpdate req) {
        long userId = userId(jwt);
        Preferences p = preferences.findById(userId).orElseGet(() -> new Preferences(userId));
        req.applyTo(p);
        return ApiResponse.ok(PreferencesView.from(preferences.save(p)));
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
