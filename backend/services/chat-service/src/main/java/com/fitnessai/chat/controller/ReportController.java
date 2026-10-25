package com.fitnessai.chat.controller;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.service.WeeklyReportService;
import com.fitnessai.chat.service.WeeklyReportService.ReportView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** The caller's weekly report: the latest one, and generate-if-due (or force a fresh one). */
@RestController
@RequestMapping("/api/v1/chat/reports/weekly")
public class ReportController {

    private final WeeklyReportService reports;

    public ReportController(WeeklyReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> out = new HashMap<>();
        out.put("dueWeekStart", reports.dueWeek());
        out.put("report", reports.latest(Long.parseLong(jwt.getSubject())).orElse(null));
        return ApiResponse.ok(out);
    }

    /** Idempotent per week: returns the existing report unless force=true. */
    @PostMapping
    public ApiResponse<ReportView> generate(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "false") boolean force,
                                            @RequestParam(defaultValue = "en") String language) {
        return ApiResponse.ok(reports.generate(Long.parseLong(jwt.getSubject()), jwt.getTokenValue(), force, "ar".equals(language)));
    }
}
