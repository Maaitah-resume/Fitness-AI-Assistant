package com.fitnessai.profile;

import com.fitnessai.profile.security.SecurityConfig;
import com.fitnessai.profile.service.ProfileService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:progress;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class ProgressIntegrationTest {

    private static final String BASE = "/api/v1/profile/progress/me";

    @Autowired MockMvc mvc;
    @Autowired ProfileService profileService;

    @Test
    void weightAndLiftLogFeedTheSummaryAndProfile() throws Exception {
        mvc.perform(get(BASE).with(user(7))).andExpect(jsonPath("$.data.summary").value("(no training log yet)"));

        weight(7, "2026-10-01", 60.0);
        weight(7, "2026-10-20", 61.0);
        weight(7, "2026-10-20", 61.5); // same day replaces
        // newest weigh-in becomes the profile weight
        mvc.perform(get("/api/v1/profile/me").with(user(7))).andExpect(jsonPath("$.data.weightKg").value(61.5));

        lift(7, "{\"date\":\"2026-10-02\",\"exercise\":\"Back Squat\",\"weightKg\":60,\"reps\":5,\"sets\":3}");
        lift(7, "{\"date\":\"2026-10-20\",\"exercise\":\"back  squat\",\"weightKg\":70,\"reps\":5}");
        String body = mvc.perform(get(BASE).with(user(7)))
                .andExpect(jsonPath("$.data.weights", hasSize(2)))
                .andExpect(jsonPath("$.data.lifts", hasSize(2)))
                .andExpect(jsonPath("$.data.lifts[1].estimatedOneRepMax").value(81.7))
                .andExpect(jsonPath("$.data.summary", containsString("Body weight: 61.5 kg on 2026-10-20")))
                .andExpect(jsonPath("$.data.summary", containsString("change +1.5 kg")))
                // both spellings group as one exercise
                .andExpect(jsonPath("$.data.summary", containsString("60.0 kg x5 for 3 sets on 2026-10-02 -> 70.0 kg x5 on 2026-10-20")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(BASE + "/summary").with(user(7))).andExpect(jsonPath("$.data.summary", containsString("2 sessions")));

        // validation, and another user's entries are invisible and undeletable
        mvc.perform(post(BASE + "/lifts").with(user(7)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"exercise\":\"\",\"weightKg\":10,\"reps\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/weight").with(user(7)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2999-01-01\",\"weightKg\":60}")).andExpect(status().isBadRequest());
        int liftId = JsonPath.read(body, "$.data.lifts[0].id");
        mvc.perform(get(BASE).with(user(8))).andExpect(jsonPath("$.data.lifts", hasSize(0)));
        mvc.perform(delete(BASE + "/lifts/" + liftId).with(user(8))).andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/lifts/" + liftId).with(user(7))).andExpect(status().isOk());

        // deleting the latest weigh-in rolls the profile weight back
        int latestWeightId = JsonPath.read(body, "$.data.weights[1].id");
        mvc.perform(delete(BASE + "/weight/" + latestWeightId).with(user(7))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile/me").with(user(7))).andExpect(jsonPath("$.data.weightKg").value(60.0));

        // user-deleted event clears the log too
        profileService.deleteForUser(7);
        mvc.perform(get(BASE).with(user(7))).andExpect(jsonPath("$.data.weights", hasSize(0)))
                .andExpect(jsonPath("$.data.lifts", hasSize(0)));
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesDefaultUpdatePartiallyAndClear() throws Exception {
        String P = "/api/v1/profile/preferences/me";
        mvc.perform(get(P).with(user(21)))
                .andExpect(jsonPath("$.data.units").value("metric"))
                .andExpect(jsonPath("$.data.answerLength").value("short"))
                .andExpect(jsonPath("$.data.tone").value("friendly"))
                .andExpect(jsonPath("$.data.trainingDays").doesNotExist());
        mvc.perform(put(P).with(user(21)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"units\":\"IMPERIAL\",\"tone\":\"strict\",\"trainingDays\":4,\"equipment\":\"dumbbells\",\"injuries\":\" left knee \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.units").value("imperial"))
                .andExpect(jsonPath("$.data.answerLength").value("short")) // untouched
                .andExpect(jsonPath("$.data.injuries").value("left knee"));
        // 0 days, "none" equipment and blank injuries clear those fields
        mvc.perform(put(P).with(user(21)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainingDays\":0,\"equipment\":\"none\",\"injuries\":\"\"}"))
                .andExpect(jsonPath("$.data.trainingDays").doesNotExist())
                .andExpect(jsonPath("$.data.equipment").doesNotExist())
                .andExpect(jsonPath("$.data.injuries").doesNotExist())
                .andExpect(jsonPath("$.data.tone").value("strict"));
        mvc.perform(put(P).with(user(21)).contentType(MediaType.APPLICATION_JSON).content("{\"trainingDays\":9}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(P).with(user(22))).andExpect(jsonPath("$.data.tone").value("friendly"));
        profileService.deleteForUser(21);
        mvc.perform(get(P).with(user(21))).andExpect(jsonPath("$.data.units").value("metric"));
    }

    @Test
    void mealsTotalPerDayFeedTheSummaryAndAreOwnedByTheUser() throws Exception {
        String N = "/api/v1/profile/nutrition/me";
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString();
        meal(31, "{\"date\":\"" + yesterday + "\",\"name\":\"Oats\",\"calories\":400,\"proteinG\":15}");
        meal(31, "{\"name\":\" Chicken and rice \",\"calories\":650,\"proteinG\":45,\"carbsG\":70,\"fatG\":12,\"source\":\"photo\"}");
        meal(31, "{\"name\":\"Greek yogurt\",\"calories\":150,\"proteinG\":15}");
        String body = mvc.perform(get(N).with(user(31)))
                .andExpect(jsonPath("$.data.meals", hasSize(3)))
                .andExpect(jsonPath("$.data.meals[1].name").value("Chicken and rice"))
                .andExpect(jsonPath("$.data.meals[1].source").value("photo"))
                .andExpect(jsonPath("$.data.days", hasSize(2)))
                .andExpect(jsonPath("$.data.days[1].date").value(today))
                .andExpect(jsonPath("$.data.days[1].calories").value(800))
                .andExpect(jsonPath("$.data.days[1].meals").value(2))
                .andReturn().getResponse().getContentAsString();
        // 1,200 kcal and 75 g protein over 2 logged days
        mvc.perform(get(BASE + "/summary").with(user(31)))
                .andExpect(jsonPath("$.data.summary", containsString("3 meals on 2 days, averaging 600 kcal and 38 g protein")));

        mvc.perform(post(N).with(user(31)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"calories\":-5,\"proteinG\":1}"))
                .andExpect(status().isBadRequest());
        int id = JsonPath.read(body, "$.data.meals[0].id");
        mvc.perform(delete(N + "/" + id).with(user(32))).andExpect(status().isNotFound());
        mvc.perform(delete(N + "/" + id).with(user(31))).andExpect(status().isOk());
        profileService.deleteForUser(31);
        mvc.perform(get(N).with(user(31))).andExpect(jsonPath("$.data.meals", hasSize(0)));
    }

    private void meal(long user, String json) throws Exception {
        mvc.perform(post("/api/v1/profile/nutrition/me").with(user(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void weight(long user, String date, double kg) throws Exception {
        mvc.perform(post(BASE + "/weight").with(user(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + date + "\",\"weightKg\":" + kg + "}")).andExpect(status().isOk());
    }

    private void lift(long user, String json) throws Exception {
        mvc.perform(post(BASE + "/lifts").with(user(user)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor user(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("role", "USER"))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
