package com.fitnessai.file.client;

import com.fitnessai.file.dto.FileDtos.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/** knowledge-service calls, made as the end user (their token is forwarded). */
@Component
public class KnowledgeClient {

    private final RestClient client;

    public KnowledgeClient(@Value("${services.knowledge-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        // Indexing embeds every chunk; a long PDF can take a while.
        timeouts.setReadTimeout(Duration.ofMinutes(3));
        this.client = RestClient.builder().requestFactory(timeouts).baseUrl(baseUrl).build();
    }

    public void index(String token, String documentId, String filename, boolean shared, List<String> chunks) {
        client.post().uri("/internal/knowledge/documents")
                .headers(h -> h.setBearerAuth(token))
                .body(new IndexRequest(documentId, filename, shared, chunks))
                .retrieve()
                .toBodilessEntity();
    }

    public void delete(String token, String documentId) {
        client.delete().uri("/internal/knowledge/documents/{id}", documentId)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .toBodilessEntity();
    }
}
