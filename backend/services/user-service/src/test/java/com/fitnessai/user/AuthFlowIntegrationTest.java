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
        "spring.datasource.url=jdbc:h2:mem:users;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.key-dir=${java.io.tmpdir}/fitness-ai-test-keys"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtDecoder jwtDecoder;

    // Redis-backed collaborators: mocked so the test needs no Redis server.
    @MockitoBean TokenRevocationStore revocations;
    @MockitoBean UserEventPublisher events;

    @Test
    void fullAuthAndAdminFlow() throws Exception {
        // register + duplicate rejection
        String aliceToken = token(register("alice", "alice@example.com"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("alice", "other@example.com")))
                .andExpect(status().isBadRequest());

        // validation: too-short password
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"short\",\"email\":\"b@example.com\"}"))
                .andExpect(status().isBadRequest());

        // login: wrong password, then right password
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        aliceToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn());

        // /me with and without token
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("user"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        // non-admin is blocked from admin endpoints
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        // promote alice the "manual DB edit" way, then re-login for a token with the new role claim
        var alice = users.findByUsername("alice").orElseThrow();
        alice.setRole(Role.ADMIN);
        users.save(alice);
        String adminToken = token(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"password123\"}")).andReturn());

        long bobId = Long.parseLong(JsonPath.read(
                register("bob", "bob@example.com").getResponse().getContentAsString(), "$.data.user_id").toString());

        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.length()").value(2));
        mvc.perform(get("/api/v1/users/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(2))
                .andExpect(jsonPath("$.data.byRole.admin").value(1));
        mvc.perform(patch("/api/v1/users/" + bobId + "/role").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));

        // self-protection rules
        mvc.perform(delete("/api/v1/users/" + alice.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/users/" + alice.getId() + "/role").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());

        // delete publishes the cross-service event
        mvc.perform(delete("/api/v1/users/" + bobId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        verify(events).userDeleted(bobId);

        // logout revokes the token; a revoked token is then rejected
        String jti = jwtDecoder.decode(adminToken).getId();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        verify(revocations).revoke(eq(jti), any());
        when(revocations.isRevoked(jti)).thenReturn(true);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());

        // JWKS is public
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
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
