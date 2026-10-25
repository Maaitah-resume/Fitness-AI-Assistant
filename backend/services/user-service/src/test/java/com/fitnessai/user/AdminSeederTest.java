package com.fitnessai.user;

import com.fitnessai.user.model.Role;
import com.fitnessai.user.repository.UserRepository;
import com.fitnessai.user.security.TokenRevocationStore;
import com.fitnessai.user.service.AdminSeeder;
import com.fitnessai.user.service.UserEventPublisher;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.key-dir=${java.io.tmpdir}/fitness-ai-test-keys"
})
@AutoConfigureMockMvc
class AdminSeederTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdminSeeder seeder;

    @MockitoBean TokenRevocationStore revocations;
    @MockitoBean UserEventPublisher events;

    @Test
    void seedAdminCanLogInResetPasswordsAndIsRestoredOnRestart() throws Exception {
        // created on startup with the default credentials
        String admin = login("admin", "Admin@12345");
        assertThat(users.findByUsername("admin").orElseThrow().getRole()).isEqualTo(Role.ADMIN);

        // admin resets another user's password
        String bobId = JsonPath.read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"password123\",\"email\":\"bob@example.com\"}"))
                .andReturn().getResponse().getContentAsString(), "$.data.user_id").toString();
        mvc.perform(patch("/api/v1/users/" + bobId + "/password").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/users/" + bobId + "/password").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"brandnew123\"}")).andExpect(status().isOk());
        String bob = login("bob", "brandnew123");
        // ...and a normal user can't
        mvc.perform(patch("/api/v1/users/" + bobId + "/password").header("Authorization", "Bearer " + bob)
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"hacked12345\"}")).andExpect(status().isForbidden());

        // a demoted seed admin gets the role back on the next start; a changed password is kept
        var seeded = users.findByUsername("admin").orElseThrow();
        seeded.setRole(Role.USER);
        seeded.setPasswordHash(users.findByUsername("bob").orElseThrow().getPasswordHash());
        users.save(seeded);
        seeder.run(null);
        assertThat(users.findByUsername("admin").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        login("admin", "brandnew123");
    }

    private String login(String username, String password) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"))
                .andReturn().getResponse().getContentAsString(), "$.data.access_token");
    }
}
