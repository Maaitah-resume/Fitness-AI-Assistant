package com.fitnessai.profile;

import com.fitnessai.profile.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class HistoryAndHealthTest {

    @Autowired MockMvc mvc;

    @Test
    void historyShowsWeightChangeAndGoalAndLevelChanges() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        save("/api/v1/profile/me", "{\"goal\":\"fat_loss\",\"level\":\"beginner\",\"weightKg\":80}");
        save("/api/v1/profile/me", "{\"goal\":\"muscle_gain\"}");
        save("/api/v1/profile/me", "{\"goal\":\"muscle_gain\",\"age\":30}"); // unchanged goal: no event
        mvc.perform(post("/api/v1/profile/progress/me/weight").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"" + today.minusDays(10) + "\",\"weightKg\":80}"));
        mvc.perform(post("/api/v1/profile/progress/me/weight").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weightKg\":78.4}"));

        mvc.perform(get("/api/v1/profile/me/history").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startWeightKg").value(80.0))
                .andExpect(jsonPath("$.data.currentWeightKg").value(78.4))
                .andExpect(jsonPath("$.data.weightChangeKg").value(-1.6))
                .andExpect(jsonPath("$.data.weighIns").value(2))
                .andExpect(jsonPath("$.data.since").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.data.events[?(@.type=='goal')].to").value(org.hamcrest.Matchers.contains("muscle_gain", "fat_loss")))
                .andExpect(jsonPath("$.data.events[?(@.type=='goal' && @.to=='muscle_gain')].from").value(hasItem("fat_loss")))
                .andExpect(jsonPath("$.data.events[?(@.type=='level')]").isNotEmpty())
                .andExpect(jsonPath("$.data.events[-1].type").value("joined"));
    }

    @Test
    void healthNotesAreSavedAndCleared() throws Exception {
        mvc.perform(put("/api/v1/profile/preferences/me").with(user(52)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"injuries\":\"left knee\",\"allergies\":\"peanuts\",\"diet\":[\"halal\",\"LACTOSE_FREE\"]}"))
                .andExpect(jsonPath("$.data.allergies").value("peanuts"))
                .andExpect(jsonPath("$.data.diet[0]").value("halal"))
                .andExpect(jsonPath("$.data.diet[1]").value("lactose_free"));
        mvc.perform(put("/api/v1/profile/preferences/me").with(user(52)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diet\":[\"carnivore\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/profile/preferences/me").with(user(52)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allergies\":\"\",\"diet\":[]}"))
                .andExpect(jsonPath("$.data.allergies").doesNotExist())
                .andExpect(jsonPath("$.data.diet").isEmpty())
                .andExpect(jsonPath("$.data.injuries").value("left knee"));
    }

    private void save(String url, String body) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url).with(user())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private static JwtRequestPostProcessor user() {
        return user(51);
    }

    private static JwtRequestPostProcessor user(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("role", "USER")).authorities(SecurityConfig.roleAuthorities());
    }
}
