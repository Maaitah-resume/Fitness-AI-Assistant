package com.fitnessai.knowledge;

import com.fitnessai.knowledge.graph.GraphRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wiring, auth and input-guard checks with the external stores mocked out. */
@SpringBootTest(properties = "events.redis.enabled=false")
@AutoConfigureMockMvc
class KnowledgeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean VectorStore vectorStore;
    @MockitoBean GraphRepository graphRepository;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/internal/knowledge/retrieve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"squats\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCannotIndexSharedDocuments() throws Exception {
        mvc.perform(post("/internal/knowledge/documents")
                        .with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":\"doc-1\",\"filename\":\"a.pdf\",\"shared\":true,\"chunks\":[\"x\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void documentIdCannotCarryFilterSyntax() throws Exception {
        String injection = "{\"documentId\":\"x' || scope == 'shared\",\"filename\":\"a.pdf\",\"shared\":false,\"chunks\":[\"x\"]}";
        mvc.perform(post("/internal/knowledge/documents")
                        .with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(injection))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/internal/knowledge/documents/x'%20||%20true")
                        .with(jwt().jwt(j -> j.subject("7").claim("role", "USER"))))
                .andExpect(status().isBadRequest());
        verify(vectorStore, never()).delete(anyString());
    }

    @Test
    void retrieveReturnsEnvelopeEvenWithEmptyStores() throws Exception {
        mvc.perform(post("/internal/knowledge/retrieve")
                        .with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"squats\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.context").value(""));
    }
}
