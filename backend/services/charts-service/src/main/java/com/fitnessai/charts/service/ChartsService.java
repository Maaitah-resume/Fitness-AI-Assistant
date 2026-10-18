package com.fitnessai.charts.service;

import com.fitnessai.charts.client.ServiceClient;
import com.fitnessai.charts.client.ServiceClient.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds chart-ready payloads (KPIs, time series, distributions, a per-user
 * status table) from the other services' stats endpoints. Holds no data of
 * its own. Downstream calls run in parallel on virtual threads; a failed
 * call blanks only its own section and is reported under "unavailable".
 */
@org.springframework.stereotype.Service
public class ChartsService {

    private static final Logger log = LoggerFactory.getLogger(ChartsService.class);
    static final Duration ADMIN_TTL = Duration.ofSeconds(60);
    static final Duration USER_TTL = Duration.ofSeconds(15);
    static final long ACTIVE_WITHIN_DAYS = 7;

    private final ServiceClient client;
    private final ChartCache cache;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChartsService(ServiceClient client, ChartCache cache) {
        this.client = client;
        this.cache = cache;
    }

    // ── Own dashboard ───────────────────────────────────────────────────────

    public Map<String, Object> me(long userId, String token) {
        return cache.getOrCompute("charts:me:" + userId, USER_TTL, () -> {
            Fetch f = new Fetch(token);
            var profile = f.get(Service.PROFILE, "/api/v1/profile/me");
            var chat = f.get(Service.CHAT, "/api/v1/chat/stats/me");
            var files = f.get(Service.FILE, "/api/v1/files");
            f.await();

            Map<String, Object> p = profile.join();
            Map<String, Object> c = chat.join();
            List<Map<String, Object>> fileList = list(files.join(), "files");

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("conversations", num(c, "conversations"));
            kpis.put("messagesSent", num(c, "messagesSent"));
            kpis.put("lastActive", c.get("lastActive"));
            kpis.put("files", (long) fileList.size());
            kpis.put("generatedDocuments", fileList.stream().filter(x -> "generated".equals(x.get("type"))).count());

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", p.isEmpty() ? null : p);
            out.put("bmiCategory", bmiCategory(p.get("bmi")));
            out.put("kpis", kpis);
            out.put("series", Map.of("messagesByDay", c.getOrDefault("messagesByDay", Map.of())));
            return f.finish(out);
        });
    }

    // ── Admin dashboards ────────────────────────────────────────────────────

    public Map<String, Object> adminOverview(String token) {
        return cache.getOrCompute("charts:admin:overview", ADMIN_TTL, () -> {
            Fetch f = new Fetch(token);
            var users = f.get(Service.USER, "/api/v1/users/admin/stats");
            var profiles = f.get(Service.PROFILE, "/api/v1/profile/admin/stats");
            var chat = f.get(Service.CHAT, "/api/v1/chat/admin/stats");
            var files = f.get(Service.FILE, "/api/v1/files/admin/stats");
            f.await();

            Map<String, Object> u = users.join();
            Map<String, Object> p = profiles.join();
            Map<String, Object> c = chat.join();
            Map<String, Object> fl = files.join();

            Map<String, Object> kpis = new LinkedHashMap<>();
            kpis.put("totalUsers", num(u, "totalUsers"));
            kpis.put("admins", num(map(u, "byRole"), "admin"));
            kpis.put("profilesCreated", num(p, "totalProfiles"));
            kpis.put("averageAge", p.get("averageAge"));
            kpis.put("conversations", num(c, "totalConversations"));
            kpis.put("userMessages", num(c, "totalUserMessages"));
            kpis.put("assistantMessages", num(c, "totalAssistantMessages"));
            kpis.put("activeUsersLast7Days", num(c, "activeUsersLast7Days"));
            kpis.put("files", num(fl, "totalFiles"));
            kpis.put("storageBytes", num(fl, "totalBytes"));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kpis", kpis);
            out.put("series", Map.of(
                    "signupsByDay", u.getOrDefault("signupsByDay", Map.of()),
                    "messagesByDay", c.getOrDefault("messagesByDay", Map.of())));
            out.put("distributions", Map.of(
                    "roles", map(u, "byRole"),
                    "goals", map(p, "byGoal"),
                    "levels", map(p, "byLevel"),
                    "genders", map(p, "byGender"),
                    "fileTypes", map(fl, "byType"),
                    "fileStatuses", map(fl, "byStatus")));
            return f.finish(out);
        });
    }

    /** Every user with profile completeness and chat activity joined in, plus a derived status. */
    public Map<String, Object> adminUsers(String token) {
        return cache.getOrCompute("charts:admin:users", ADMIN_TTL, () -> {
            Fetch f = new Fetch(token);
            var users = f.get(Service.USER, "/api/v1/users");
            var profiles = f.get(Service.PROFILE, "/api/v1/profile/admin/all");
            var activity = f.get(Service.CHAT, "/api/v1/chat/admin/activity");
            f.await();

            Map<Long, Map<String, Object>> profileByUser = byId(list(profiles.join(), "profiles"), "userId");
            Map<Long, Map<String, Object>> activityByUser = byId(list(activity.join(), "activity"), "userId");
            Instant activeCutoff = Instant.now().minus(ACTIVE_WITHIN_DAYS, ChronoUnit.DAYS);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> user : list(users.join(), "users")) {
                long id = num(user, "id");
                Map<String, Object> prof = profileByUser.getOrDefault(id, Map.of());
                Map<String, Object> act = activityByUser.getOrDefault(id, Map.of());
                Instant lastActive = instant(act.get("lastActive"));

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("username", user.get("username"));
                row.put("email", user.get("email"));
                row.put("role", user.get("role"));
                row.put("joined", user.get("created_at"));
                row.put("profileComplete", Boolean.TRUE.equals(prof.get("complete")));
                row.put("goal", prof.get("goal"));
                row.put("level", prof.get("level"));
                row.put("bmi", prof.get("bmi"));
                row.put("conversations", num(act, "conversations"));
                row.put("messagesSent", num(act, "messagesSent"));
                row.put("lastActive", lastActive == null ? null : lastActive.toString());
                row.put("status", status(lastActive, activeCutoff));
                rows.add(row);
            }
            rows.sort(Comparator.comparing((Map<String, Object> r) -> instant(r.get("lastActive")),
                    Comparator.nullsLast(Comparator.reverseOrder())));

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            for (String s : List.of("active", "idle", "new")) {
                statusCounts.put(s, rows.stream().filter(r -> s.equals(r.get("status"))).count());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("users", rows);
            out.put("statusCounts", statusCounts);
            return f.finish(out);
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Runs a dashboard's downstream calls concurrently and records which ones failed. */
    private final class Fetch {
        private final String token;
        private final List<String> unavailable = Collections.synchronizedList(new ArrayList<>());
        private final List<CompletableFuture<?>> pending = new ArrayList<>();

        Fetch(String token) {
            this.token = token;
        }

        CompletableFuture<Map<String, Object>> get(Service service, String path) {
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return client.data(service, path, token);
                } catch (Exception e) {
                    log.warn("{}{} unavailable: {}", service.label, path, e.getMessage());
                    if (!unavailable.contains(service.label)) {
                        unavailable.add(service.label);
                    }
                    return Map.<String, Object>of();
                }
            }, executor);
            pending.add(future);
            return future;
        }

        void await() {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
        }

        Map<String, Object> finish(Map<String, Object> out) {
            out.put("unavailable", List.copyOf(unavailable));
            out.put("generatedAt", Instant.now().toString());
            return out;
        }
    }

    static String status(Instant lastActive, Instant activeCutoff) {
        if (lastActive == null) {
            return "new";
        }
        return lastActive.isAfter(activeCutoff) ? "active" : "idle";
    }

    static String bmiCategory(Object bmi) {
        if (!(bmi instanceof Number n)) {
            return null;
        }
        double v = n.doubleValue();
        if (v < 18.5) return "underweight";
        if (v < 25) return "normal";
        if (v < 30) return "overweight";
        return "obese";
    }

    /** Jackson may render Instants as ISO strings or epoch-second numbers depending on config. */
    static Instant instant(Object v) {
        if (v instanceof String s && !s.isBlank()) {
            return Instant.parse(s);
        }
        if (v instanceof Number n) {
            double seconds = n.doubleValue();
            return Instant.ofEpochSecond((long) seconds, (long) ((seconds % 1) * 1_000_000_000));
        }
        return null;
    }

    static long num(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<String, Object> m, String key) {
        return m.get(key) instanceof Map<?, ?> v ? (Map<String, Object>) v : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        return m.get(key) instanceof List<?> v ? (List<Map<String, Object>>) v : List.of();
    }

    private static Map<Long, Map<String, Object>> byId(List<Map<String, Object>> rows, String idKey) {
        Map<Long, Map<String, Object>> out = new HashMap<>();
        rows.forEach(r -> out.put(num(r, idKey), r));
        return out;
    }
}
