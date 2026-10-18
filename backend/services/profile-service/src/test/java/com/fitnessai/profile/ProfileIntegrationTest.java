package com.fitnessai.profile;

import com.fitnessai.profile.security.SecurityConfig;
import com.fitnessai.profile.service.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profiles;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class ProfileIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProfileService profileService;

    @Test
    void ownProfileAndAdminViews() throws Exception {
        // no profile yet -> empty, incomplete
        mvc.perform(get("/api/v1/profile/me").with(user(7, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.complete").value(false));

        // partial update, case-insensitive enums, BMI computed
        mvc.perform(put("/api/v1/profile/me").with(user(7, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":30,\"gender\":\"Female\",\"heightCm\":170,\"weightKg\":65}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("female"))
                .andExpect(jsonPath("$.data.bmi").value(22.5))
                .andExpect(jsonPath("$.data.complete").value(false));
        mvc.perform(put("/api/v1/profile/me").with(user(7, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"FAT_LOSS\",\"level\":\"beginner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(30))
                .andExpect(jsonPath("$.data.goal").value("fat_loss"))
                .andExpect(jsonPath("$.data.complete").value(true));

        // validation
        mvc.perform(put("/api/v1/profile/me").with(user(7, "USER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":5,\"goal\":\"get_huge\"}"))
                .andExpect(status().isBadRequest());

        // auth boundaries
        mvc.perform(get("/api/v1/profile/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile/admin/stats").with(user(7, "USER"))).andExpect(status().isForbidden());

        // admin views
        mvc.perform(put("/api/v1/profile/me").with(user(8, "USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"age\":40,\"goal\":\"muscle_gain\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile/admin/stats").with(user(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProfiles").value(2))
                .andExpect(jsonPath("$.data.averageAge").value(35.0))
                .andExpect(jsonPath("$.data.byGoal.fat_loss").value(1))
                .andExpect(jsonPath("$.data.byGoal.muscle_gain").value(1));
        mvc.perform(get("/api/v1/profile/admin/users/7").with(user(1, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("beginner"));
        mvc.perform(get("/api/v1/profile/admin/all").with(user(1, "ADMIN")))
                .andExpect(jsonPath("$.data.profiles.length()").value(2));
        mvc.perform(get("/api/v1/profile/admin/all").with(user(7, "USER"))).andExpect(status().isForbidden());

        // user-deleted event handling path
        profileService.deleteForUser(7);
        mvc.perform(get("/api/v1/profile/me").with(user(7, "USER")))
                .andExpect(jsonPath("$.data.age").doesNotExist());
    }

    private static JwtRequestPostProcessor user(long id, String role) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("role", role))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
