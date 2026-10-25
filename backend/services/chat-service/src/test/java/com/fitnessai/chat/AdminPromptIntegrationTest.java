package com.fitnessai.chat;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.memory.LongTermMemoryExtractor;
import com.fitnessai.chat.security.SecurityConfig;
import com.fitnessai.chat.service.AssistantModel;
import com.fitnessai.chat.service.SystemPrompt;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prompt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class AdminPromptIntegrationTest {

    private static final String P = "/api/v1/chat/admin/prompt";
    private static final String CUSTOM = "You are IronCoach, a blunt strength coach for powerlifters. Keep every answer under five sentences.";

    @Autowired MockMvc mvc;

    @MockitoBean AssistantModel model;
    @MockitoBean DownstreamClients clients;
    @MockitoBean LongTermMemoryExtractor longTermMemory;
    @MockitoBean StringRedisTemplate redis;

    @Test
    void adminEditsRestoresAndResetsTheCoachPrompt() throws Exception {
        mvc.perform(get(P).with(user(7, "USER"))).andExpect(status().isForbidden());
        mvc.perform(get(P).with(user(1, "ADMIN")))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.text").value(SystemPrompt.DEFAULT_BASE))
                .andExpect(jsonPath("$.data.appended.length()").value(6));

        // too short is rejected; a real edit becomes the active version
        mvc.perform(put(P).with(user(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Be nice.\"}"))
                .andExpect(status().isBadRequest());
        String state = mvc.perform(put(P).with(user(1, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + CUSTOM + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andExpect(jsonPath("$.data.current.createdByName").value("root"))
                .andExpect(jsonPath("$.data.current.action").value("edit"))
                .andReturn().getResponse().getContentAsString();
        int editId = JsonPath.read(state, "$.data.current.id");

        // the next chat message is built on the edited prompt, with the system sections still appended
        when(model.reply(anyString(), anyList(), anyString(), any())).thenReturn("Lift heavy.");
        mvc.perform(post("/api/v1/chat/send").with(user(7, "USER")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What should I do today?\"}")).andExpect(status().isOk());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).reply(prompt.capture(), anyList(), anyString(), any());
        assertThat(prompt.getValue()).startsWith("You are IronCoach").contains("=== USER PREFERENCES ===", "=== USER DATA", "--- TRAINING LOG ---")
                .doesNotContain("You are GymAI");

        // reset to the built-in prompt, then restore the edit from history
        mvc.perform(post(P + "/reset").with(user(1, "ADMIN")))
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.current.action").value("reset"));
        mvc.perform(post(P + "/versions/" + editId + "/restore").with(user(1, "ADMIN")))
                .andExpect(jsonPath("$.data.text").value(CUSTOM))
                .andExpect(jsonPath("$.data.current.action").value("restore #" + editId))
                .andExpect(jsonPath("$.data.history.length()").value(3));
        mvc.perform(get(P + "/versions/" + editId).with(user(1, "ADMIN"))).andExpect(jsonPath("$.data").value(CUSTOM));
        mvc.perform(post(P + "/versions/999/restore").with(user(1, "ADMIN"))).andExpect(status().isNotFound());
    }

    private static JwtRequestPostProcessor user(long id, String role) {
        return jwt().jwt(j -> j.tokenValue("tok-" + id).subject(String.valueOf(id)).claim("role", role).claim("username", "root"))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
