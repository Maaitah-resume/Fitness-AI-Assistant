package com.fitnessai.profile;

import com.fitnessai.profile.security.SecurityConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:adminbadges;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class AdminBadgesTest {

    private static final String B = "/api/v1/profile/admin/badges";

    @Autowired MockMvc mvc;

    @Test
    void adminDefinesABadgeAndUsersEarnItByItsRule() throws Exception {
        // starter badges are seeded; users can't manage badges
        mvc.perform(get(B).with(as(61, "USER"))).andExpect(status().isForbidden());
        mvc.perform(get(B).with(as(1, "ADMIN")))
                .andExpect(jsonPath("$.data.badges[?(@.code=='first_workout')].rule").value(hasItem("workouts")));
        mvc.perform(get(B + "/options").with(as(1, "ADMIN")))
                .andExpect(jsonPath("$.data.rules[?(@.key=='exercise_sessions')].needsExercise").value(hasItem(true)));

        // a lift rule without an exercise, or an unknown rule, is rejected
        mvc.perform(post(B).with(as(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(badge("Bench regular", "exercise_sessions", 3, null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(B).with(as(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(badge("Nope", "jumping", 3, null)))
                .andExpect(status().isBadRequest());

        String created = mvc.perform(post(B).with(as(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(badge("Bench regular!", "exercise_sessions", 3, "Bench press")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("bench_regular"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.data.id")).longValue();

        // the user benches on 3 days: the badge is earned on the third
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int back = 4; back >= 0; back -= 2) {
            mvc.perform(post("/api/v1/profile/progress/me/lifts").with(as(62, "USER")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"" + today.minusDays(back) + "\",\"exercise\":\"bench  Press\",\"weightKg\":40,\"reps\":8}"));
        }
        mvc.perform(put("/api/v1/profile/me").with(as(62, "USER")).contentType(MediaType.APPLICATION_JSON).content("{\"age\":30}"));
        mvc.perform(get("/api/v1/profile/achievements/me").with(as(62, "USER")))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bench_regular')].earned").value(contains(true)))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bench_regular')].earnedOn").value(contains(today.toString())))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bench_regular')].name").value(contains("Bench regular!")))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bench_regular')].icon").value(contains("star")));
        mvc.perform(get(B).with(as(1, "ADMIN")))
                .andExpect(jsonPath("$.data.badges[?(@.code=='bench_regular')].earnedBy").value(contains(1)));

        // switched off, it disappears for users; deleted, it's gone
        mvc.perform(put(B + "/" + id).with(as(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(badge("Bench regular!", "exercise_sessions", 3, "Bench press").replace("}", ",\"active\":false}")))
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get("/api/v1/profile/achievements/me").with(as(62, "USER")))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bench_regular')]").value(empty()));
        mvc.perform(delete(B + "/" + id).with(as(1, "ADMIN"))).andExpect(status().isOk());
        mvc.perform(delete(B + "/" + id).with(as(1, "ADMIN"))).andExpect(status().isNotFound());
    }

    private static String badge(String name, String rule, double threshold, String exercise) {
        return "{\"name\":\"" + name + "\",\"icon\":\"star\",\"color\":\"ember\",\"rule\":\"" + rule + "\",\"threshold\":" + threshold
                + (exercise == null ? "" : ",\"exercise\":\"" + exercise + "\"") + "}";
    }

    private static JwtRequestPostProcessor as(long id, String role) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("role", role)).authorities(SecurityConfig.roleAuthorities());
    }
}
