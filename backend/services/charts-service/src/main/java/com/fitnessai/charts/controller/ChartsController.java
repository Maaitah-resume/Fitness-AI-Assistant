package com.fitnessai.charts.controller;

import com.fitnessai.charts.dto.ApiResponse;
import com.fitnessai.charts.service.ChartsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/charts")
public class ChartsController {

    private final ChartsService charts;

    public ChartsController(ChartsService charts) {
        this.charts = charts;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(charts.me(Long.parseLong(jwt.getSubject()), jwt.getTokenValue()));
    }

    // Checked here as well as downstream, so a non-admin gets a clean 403
    // instead of a dashboard full of "unavailable" sections.
    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(charts.adminOverview(jwt.getTokenValue()));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> users(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(charts.adminUsers(jwt.getTokenValue()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Admin privileges required."));
    }
}
