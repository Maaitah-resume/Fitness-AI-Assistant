package com.fitnessai.chat;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.RetrievedChunk;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import com.fitnessai.chat.memory.LongTermMemoryExtractor;
import com.fitnessai.chat.memory.ShortTermMemory.Turn;
import com.fitnessai.chat.model.ChatMessage;
import com.fitnessai.chat.security.SecurityConfig;
import com.fitnessai.chat.service.AssistantModel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChatFlowIntegrationTest {

    @Autowired MockMvc mvc;

    @MockitoBean AssistantModel model;
    @MockitoBean DownstreamClients clients;
    @MockitoBean LongTermMemoryExtractor longTermMemory;
    // Unstubbed: every Redis call fails, exercising ShortTermMemory's database fallback.
    @MockitoBean StringRedisTemplate redis;

    @Test
    void fullConversationLifecycle() throws Exception {
        when(clients.myProfile("tok-7")).thenReturn(
                new ProfileSnapshot(30, "female", null, null, null, "fat_loss", "beginner", true));
        when(clients.retrieve(eq("tok-7"), anyString())).thenReturn(new RetrievedContext(
                "[Document excerpts]\n(1) plan.pdf: Squat 3x8", List.of(new RetrievedChunk("Squat 3x8", "plan.pdf", "d1", 0.9)),
                List.of(), List.of()));
        when(model.reply(anyString(), anyList(), anyString(), any())).thenReturn("Do squats.", "Then lunges.");

        // first turn creates the conversation
        String first = mvc.perform(post("/api/v1/chat/send").with(user(7, "USER", "tok-7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  What should I train today?  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("Do squats."))
                .andExpect(jsonPath("$.data.sources[0]").value("plan.pdf"))
                .andExpect(jsonPath("$.data.degraded").value(false))
                .andReturn().getResponse().getContentAsString();
        long chatId = ((Number) JsonPath.read(first, "$.data.chat_id")).longValue();

        // the system prompt carried profile + retrieved context
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(model).reply(system.capture(), anyList(), eq("What should I train today?"), any());
        assertThat(system.getValue()).contains("goal=fat_loss", "plan.pdf: Squat 3x8");

        // second turn sees the first turn as history
        mvc.perform(post("/api/v1/chat/send").with(user(7, "USER", "tok-7"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"And after?\",\"chat_id\":" + chatId + "}"))
                .andExpect(jsonPath("$.data.response").value("Then lunges."));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Turn>> history = ArgumentCaptor.forClass(List.class);
        verify(model).reply(anyString(), history.capture(), eq("And after?"), any());
        assertThat(history.getValue()).extracting(Turn::content)
                .containsExactly("What should I train today?", "Do squats.");

        // listing and history
        mvc.perform(get("/api/v1/chat/recent").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.chats[0].title").value("What should I train today?"))
                .andExpect(jsonPath("$.data.chats[0].last_message").value("Then lunges."));
        mvc.perform(get("/api/v1/chat/" + chatId + "/messages").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.messages.length()").value(4))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"))
                .andExpect(jsonPath("$.data.messages[1].message").value("Do squats."));

        // another user can't read, continue or delete it
        mvc.perform(get("/api/v1/chat/" + chatId + "/messages").with(user(8, "USER", "tok-8")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/chat/send").with(user(8, "USER", "tok-8"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\",\"chat_id\":" + chatId + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/chat/" + chatId).with(user(8, "USER", "tok-8")))
                .andExpect(status().isNotFound());

        // admin oversight + stats; plain users are blocked
        mvc.perform(get("/api/v1/chat/admin/stats").with(user(7, "USER", "tok-7"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat/admin/stats").with(user(1, "ADMIN", "tok-1")))
                .andExpect(jsonPath("$.data.totalConversations").value(1))
                .andExpect(jsonPath("$.data.totalUserMessages").value(2))
                .andExpect(jsonPath("$.data.activeUsersLast7Days").value(1));
        mvc.perform(get("/api/v1/chat/admin/chats/" + chatId + "/messages").with(user(1, "ADMIN", "tok-1")))
                .andExpect(jsonPath("$.data.user_id").value(7));
        mvc.perform(get("/api/v1/chat/admin/activity").with(user(1, "ADMIN", "tok-1")))
                .andExpect(jsonPath("$.data.activity.length()").value(1))
                .andExpect(jsonPath("$.data.activity[0].userId").value(7))
                .andExpect(jsonPath("$.data.activity[0].conversations").value(1))
                .andExpect(jsonPath("$.data.activity[0].messagesSent").value(2));
        mvc.perform(get("/api/v1/chat/stats/me").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.conversations").value(1))
                .andExpect(jsonPath("$.data.messagesSent").value(2));

        // owner deletes
        mvc.perform(delete("/api/v1/chat/" + chatId).with(user(7, "USER", "tok-7"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/chat/recent").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.chats.length()").value(0));
    }

    @Test
    void llmOutageDegradesGracefullyAndDependenciesAreOptional() throws Exception {
        when(clients.myProfile(anyString())).thenThrow(new RuntimeException("profile-service down"));
        when(clients.retrieve(anyString(), anyString())).thenThrow(new RuntimeException("knowledge-service down"));
        when(model.reply(anyString(), anyList(), anyString(), any())).thenThrow(new RuntimeException("OpenAI 401"));

        String body = mvc.perform(post("/api/v1/chat/send").with(user(9, "USER", "tok-9"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andReturn().getResponse().getContentAsString();
        long chatId = ((Number) JsonPath.read(body, "$.data.chat_id")).longValue();

        // user's message kept, no fake assistant message stored
        mvc.perform(get("/api/v1/chat/" + chatId + "/messages").with(user(9, "USER", "tok-9")))
                .andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].role").value("user"));
    }

    @Test
    void longTermMemoryRunsEverySixthUserMessage() throws Exception {
        when(model.reply(anyString(), anyList(), anyString(), any())).thenReturn("ok");
        Long chatId = null;
        for (int i = 1; i <= 7; i++) {
            String body = mvc.perform(post("/api/v1/chat/send").with(user(11, "USER", "tok-11"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"msg " + i + "\"" + (chatId == null ? "" : ",\"chat_id\":" + chatId) + "}"))
                    .andReturn().getResponse().getContentAsString();
            chatId = ((Number) JsonPath.read(body, "$.data.chat_id")).longValue();
            if (i < 6) {
                verify(longTermMemory, never()).extractAndStore(anyString(), anyList());
            }
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Turn>> turns = ArgumentCaptor.forClass(List.class);
        verify(longTermMemory, times(1)).extractAndStore(eq("tok-11"), turns.capture());
        assertThat(turns.getValue()).last().extracting(Turn::role).isEqualTo(ChatMessage.Role.ASSISTANT);
        assertThat(turns.getValue()).hasSize(12);
    }

    @Test
    void rejectsBlankMessagesAndAnonymousCalls() throws Exception {
        mvc.perform(post("/api/v1/chat/send").with(user(7, "USER", "t"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/recent")).andExpect(status().isUnauthorized());
    }

    private static JwtRequestPostProcessor user(long id, String role, String token) {
        return jwt().jwt(j -> j.tokenValue(token).subject(String.valueOf(id)).claim("role", role))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
