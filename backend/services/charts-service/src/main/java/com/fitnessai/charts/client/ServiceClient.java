package com.fitnessai.charts.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reads the "data" object of another service's {status, data} envelope,
 * forwarding the caller's own bearer token — so each service still enforces
 * its own rules (a non-admin token gets 403 from admin stats endpoints).
 */
@Component
public class ServiceClient {

    public enum Service {
        USER("user-service"), PROFILE("profile-service"), CHAT("chat-service"), FILE("file-service");

        public final String label;

        Service(String label) {
            this.label = label;
        }
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private final Map<Service, RestClient> clients = new EnumMap<>(Service.class);

    public ServiceClient(@Value("${services.user-service.base-url}") String userUrl,
                         @Value("${services.profile-service.base-url}") String profileUrl,
                         @Value("${services.chat-service.base-url}") String chatUrl,
                         @Value("${services.file-service.base-url}") String fileUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(8));
        RestClient.Builder base = RestClient.builder().requestFactory(timeouts);
        clients.put(Service.USER, base.clone().baseUrl(userUrl).build());
        clients.put(Service.PROFILE, base.clone().baseUrl(profileUrl).build());
        clients.put(Service.CHAT, base.clone().baseUrl(chatUrl).build());
        clients.put(Service.FILE, base.clone().baseUrl(fileUrl).build());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> data(Service service, String path, String token) {
        Map<String, Object> body = clients.get(service).get().uri(path)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(MAP);
        Object data = body == null ? null : body.get("data");
        if (!(data instanceof Map)) {
            throw new IllegalStateException("Unexpected response from " + service.label + path);
        }
        return (Map<String, Object>) data;
    }
}
