package com.fitnessai.user;

import com.fitnessai.user.model.Role;
import com.fitnessai.user.repository.UserRepository;
import com.fitnessai.user.security.TokenRevocationStore;
import com.fitnessai.user.service.UserEventPublisher;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:selfservice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.key-dir=${java.io.tmpdir}/fitness-ai-test-keys",
        "seed.admin.enabled=false"
})
@AutoConfigureMockMvc
class AccountSelfServiceTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    // Redis-backed collaborators: mocked so the test needs no Redis server.
    @MockitoBean TokenRevocationStore revocations;
    @MockitoBean UserEventPublisher events;

    @Test
    void selfServicePasswordChangeAndAccountDeletion() throws Exception {
        String carolId = JsonPath.read(register("carol", "carol@example.com").getResponse().getContentAsString(), "$.data.user_id").toString();
        String carol = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"password123\"}")).andReturn());

        // wrong current password, too-short and unchanged new passwords are rejected
        changePassword(carol, "nope", "newpassword1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect."));
        changePassword(carol, "password123", "short").andExpect(status().isBadRequest());
        changePassword(carol, "password123", "password123").andExpect(status().isBadRequest());
        changePassword(carol, "password123", "newpassword1").andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"password123\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"newpassword1\"}")).andExpect(status().isOk());

        // deleting needs the password; then the user is gone and every service is told
        mvc.perform(delete("/api/v1/auth/me").header("Authorization", "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/auth/me").header("Authorization", "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"newpassword1\"}")).andExpect(status().isOk());
        verify(events).userDeleted(Long.parseLong(carolId));
        verify(revocations).revoke(any(), any());
        org.assertj.core.api.Assertions.assertThat(users.findByUsername("carol")).isEmpty();

        // the only admin can't delete themselves
        register("dave", "dave@example.com");
        var dave = users.findByUsername("dave").orElseThrow();
        users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).forEach(u -> { u.setRole(Role.USER); users.save(u); });
        dave.setRole(Role.ADMIN);
        users.save(dave);
        String daveToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dave\",\"password\":\"password123\"}")).andReturn());
        mvc.perform(delete("/api/v1/auth/me").header("Authorization", "Bearer " + daveToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only admin")));
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/v1/auth/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private MvcResult register(String username, String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, email)))
                .andExpect(status().isOk()).andReturn();
    }

    private static String json(String username, String email) {
        return "{\"username\":\"" + username + "\",\"password\":\"password123\",\"email\":\"" + email + "\"}";
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.access_token");
    }
}
