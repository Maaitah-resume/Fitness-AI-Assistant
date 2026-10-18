package com.fitnessai.chat.client;

import com.fitnessai.chat.dto.ApiResponse;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.MemoryRequest;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProfileUpdate;
import com.fitnessai.chat.dto.ChatDtos.RetrieveRequest;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Calls to knowledge-, profile- and file-service. Every call forwards the end
 * user's bearer token (passed explicitly rather than read from the security
 * context, so these also work from async code), and each downstream service
 * applies its own authorization to it.
 */
@Component
public class DownstreamClients {

    private final RestClient knowledge;
    private final RestClient profile;
    private final RestClient files;

    public DownstreamClients(RestClient.Builder builder,
                             @Value("${services.knowledge-service.base-url}") String knowledgeUrl,
                             @Value("${services.profile-service.base-url}") String profileUrl,
                             @Value("${services.file-service.base-url}") String fileUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(60));
        RestClient.Builder base = builder.clone().requestFactory(timeouts);
        this.knowledge = base.clone().baseUrl(knowledgeUrl).build();
        this.profile = base.clone().baseUrl(profileUrl).build();
        this.files = base.clone().baseUrl(fileUrl).build();
    }

    public RetrievedContext retrieve(String token, String query) {
        ApiResponse<RetrievedContext> r = knowledge.post().uri("/internal/knowledge/retrieve")
                .headers(h -> h.setBearerAuth(token))
                .body(new RetrieveRequest(query, 4))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return r == null || r.data() == null ? RetrievedContext.EMPTY : r.data();
    }

    public void remember(String token, List<String> facts) {
        knowledge.post().uri("/internal/knowledge/memories")
                .headers(h -> h.setBearerAuth(token))
                .body(new MemoryRequest(facts))
                .retrieve()
                .toBodilessEntity();
    }

    public ProfileSnapshot myProfile(String token) {
        ApiResponse<ProfileSnapshot> r = profile.get().uri("/api/v1/profile/me")
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return r == null ? null : r.data();
    }

    public ProfileSnapshot updateProfile(String token, ProfileUpdate update) {
        ApiResponse<ProfileSnapshot> r = profile.put().uri("/api/v1/profile/me")
                .headers(h -> h.setBearerAuth(token))
                .body(update)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return r == null ? null : r.data();
    }

    public GeneratedFile generateDocument(String token, GenerateRequest request) {
        ApiResponse<GeneratedFile> r = files.post().uri("/api/v1/files/generate")
                .headers(h -> h.setBearerAuth(token))
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return r == null ? null : r.data();
    }
}
