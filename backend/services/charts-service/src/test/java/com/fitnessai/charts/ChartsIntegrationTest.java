package com.fitnessai.charts;

import com.fitnessai.charts.client.ServiceClient;
import com.fitnessai.charts.client.ServiceClient.Service;
import com.fitnessai.charts.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChartsIntegrationTest {

    @Autowired MockMvc mvc;
    @MockitoBean ServiceClient client;
    @MockitoBean StringRedisTemplate redis;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void cacheMisses() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void ownDashboardAggregatesAndCaches() throws Exception {
        when(client.data(Service.PROFILE, "/api/v1/profile/me", "tok-7"))
                .thenReturn(Map.of("bmi", 27.1, "goal", "fat_loss", "complete", true));
        when(client.data(Service.CHAT, "/api/v1/chat/stats/me", "tok-7"))
                .thenReturn(Map.of("conversations", 3, "messagesSent", 14, "messagesByDay", Map.of("2026-10-11", 4)));
        when(client.data(Service.FILE, "/api/v1/files", "tok-7"))
                .thenReturn(Map.of("files", List.of(Map.of("type", "upload"), Map.of("type", "generated"))));

        mvc.perform(get("/api/v1/charts/me").with(user(7, "USER", "tok-7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bmiCategory").value("overweight"))
                .andExpect(jsonPath("$.data.kpis.messagesSent").value(14))
                .andExpect(jsonPath("$.data.kpis.files").value(2))
                .andExpect(jsonPath("$.data.kpis.generatedDocuments").value(1))
                .andExpect(jsonPath("$.data.series.messagesByDay['2026-10-11']").value(4))
                .andExpect(jsonPath("$.data.unavailable.length()").value(0));
        verify(values).set(eq("charts:me:7"), anyString(), any(java.time.Duration.class));
    }

    @Test
    void aDownServiceBlanksOnlyItsSectionAndIsNotCached() throws Exception {
        when(client.data(Service.PROFILE, "/api/v1/profile/me", "tok-8")).thenReturn(Map.of());
        when(client.data(Service.CHAT, "/api/v1/chat/stats/me", "tok-8")).thenThrow(new RuntimeException("down"));
        when(client.data(Service.FILE, "/api/v1/files", "tok-8")).thenReturn(Map.of("files", List.of()));

        mvc.perform(get("/api/v1/charts/me").with(user(8, "USER", "tok-8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailable[0]").value("chat-service"))
                .andExpect(jsonPath("$.data.kpis.messagesSent").value(0))
                .andExpect(jsonPath("$.data.profile").doesNotExist());
        verify(values, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void adminOverviewIsAdminOnlyAndShapesChartData() throws Exception {
        mvc.perform(get("/api/v1/charts/admin/overview").with(user(7, "USER", "t"))).andExpect(status().isForbidden());

        when(client.data(Service.USER, "/api/v1/users/admin/stats", "tok-1")).thenReturn(Map.of(
                "totalUsers", 12, "byRole", Map.of("admin", 2, "user", 10), "signupsByDay", Map.of("2026-10-10", 3)));
        when(client.data(Service.PROFILE, "/api/v1/profile/admin/stats", "tok-1")).thenReturn(Map.of(
                "totalProfiles", 9, "averageAge", 31.5, "byGoal", Map.of("fat_loss", 5, "muscle_gain", 4)));
        when(client.data(Service.CHAT, "/api/v1/chat/admin/stats", "tok-1")).thenReturn(Map.of(
                "totalConversations", 40, "totalUserMessages", 300, "activeUsersLast7Days", 6));
        when(client.data(Service.FILE, "/api/v1/files/admin/stats", "tok-1")).thenReturn(Map.of(
                "totalFiles", 7, "totalBytes", 1048576, "byType", Map.of("upload", 5, "generated", 2)));

        mvc.perform(get("/api/v1/charts/admin/overview").with(user(1, "ADMIN", "tok-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.totalUsers").value(12))
                .andExpect(jsonPath("$.data.kpis.admins").value(2))
                .andExpect(jsonPath("$.data.kpis.activeUsersLast7Days").value(6))
                .andExpect(jsonPath("$.data.kpis.storageBytes").value(1048576))
                .andExpect(jsonPath("$.data.distributions.goals.fat_loss").value(5))
                .andExpect(jsonPath("$.data.series.signupsByDay['2026-10-10']").value(3));
    }

    @Test
    void adminUserTableJoinsAndDerivesStatus() throws Exception {
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        when(client.data(Service.USER, "/api/v1/users", "tok-1")).thenReturn(Map.of("users", List.of(
                Map.of("id", 1, "username", "admin", "role", "admin"),
                Map.of("id", 7, "username", "alice", "role", "user"),
                Map.of("id", 8, "username", "bob", "role", "user"))));
        when(client.data(Service.PROFILE, "/api/v1/profile/admin/all", "tok-1")).thenReturn(Map.of("profiles", List.of(
                Map.of("userId", 7, "complete", true, "goal", "fat_loss", "bmi", 22.5))));
        when(client.data(Service.CHAT, "/api/v1/chat/admin/activity", "tok-1")).thenReturn(Map.of("activity", List.of(
                Map.of("userId", 7, "conversations", 2, "messagesSent", 9, "lastActive", recent.toString()),
                Map.of("userId", 8, "conversations", 1, "messagesSent", 1, "lastActive", old.toString()))));

        mvc.perform(get("/api/v1/charts/admin/users").with(user(1, "ADMIN", "tok-1")))
                .andExpect(status().isOk())
                // most recently active first; never-active users last
                .andExpect(jsonPath("$.data.users[0].username").value("alice"))
                .andExpect(jsonPath("$.data.users[0].status").value("active"))
                .andExpect(jsonPath("$.data.users[0].profileComplete").value(true))
                .andExpect(jsonPath("$.data.users[0].messagesSent").value(9))
                .andExpect(jsonPath("$.data.users[1].status").value("idle"))
                .andExpect(jsonPath("$.data.users[2].username").value("admin"))
                .andExpect(jsonPath("$.data.users[2].status").value("new"))
                .andExpect(jsonPath("$.data.users[2].profileComplete").value(false))
                .andExpect(jsonPath("$.data.statusCounts.active").value(1))
                .andExpect(jsonPath("$.data.statusCounts.new").value(1));
    }

    private static JwtRequestPostProcessor user(long id, String role, String token) {
        return jwt().jwt(j -> j.tokenValue(token).subject(String.valueOf(id)).claim("role", role))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
