package com.fitnessai.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Real gateway, real routing, against a tiny in-process HTTP server standing
 * in for every downstream service. The JWT decoder is mocked (revocation is
 * covered by RevocationCheckingJwtDecoderTest), and rate limiting is switched
 * off because it needs Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class GatewayRoutingSecurityTest {

    static final HttpServer BACKEND;
    static final Map<String, Map<String, List<String>>> SEEN = new ConcurrentHashMap<>();

    static {
        try {
            BACKEND = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        BACKEND.createContext("/", exchange -> {
            SEEN.put(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()));
            byte[] body = ("{\"status\":\"success\",\"data\":{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        BACKEND.start();
    }

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        int port = BACKEND.getAddress().getPort();
        registry.add("backend.port", () -> port);
        for (String svc : List.of("USER", "PROFILE", "CHAT", "FILE", "CHARTS")) {
            registry.add(svc + "_SERVICE_HOST", () -> "localhost");
            registry.add(svc + "_SERVICE_PORT", () -> port);
        }
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
    }

    @Autowired WebTestClient web;
    @MockitoBean ReactiveJwtDecoder decoder;

    @BeforeEach
    void tokens() {
        SEEN.clear();
        when(decoder.decode(anyString())).thenReturn(Mono.error(new BadJwtException("bad token")));
        when(decoder.decode("user-token")).thenReturn(Mono.just(jwt("user-token", "7", "USER")));
        when(decoder.decode("admin-token")).thenReturn(Mono.just(jwt("admin-token", "1", "ADMIN")));
    }

    @Test
    void publicAuthEndpointsAreRoutedWithoutATokenAndCookiesAreStripped() {
        web.post().uri("/api/v1/auth/login").header(HttpHeaders.COOKIE, "session=abc").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.data.path").isEqualTo("/api/v1/auth/login");
        assertThat(SEEN.get("/api/v1/auth/login")).doesNotContainKey("Cookie");
    }

    @Test
    void protectedRoutesNeedAValidToken() {
        web.get().uri("/api/v1/profile/me").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Authentication required.");
        web.get().uri("/api/v1/profile/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged").exchange()
                .expectStatus().isUnauthorized();
        assertThat(SEEN).isEmpty();

        web.get().uri("/api/v1/profile/me").header(HttpHeaders.AUTHORIZATION, "Bearer user-token").exchange()
                .expectStatus().isOk();
        // the token itself is forwarded, so the service can re-validate it
        assertThat(SEEN.get("/api/v1/profile/me").get("Authorization")).containsExactly("Bearer user-token");
    }

    @Test
    void adminPathsAreGatedAtTheEdge() {
        for (String path : List.of("/api/v1/users", "/api/v1/users/5/role", "/api/v1/chat/admin/stats",
                "/api/v1/charts/admin/overview", "/api/v1/files/admin/all")) {
            web.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer user-token").exchange()
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.status").isEqualTo("error");
        }
        assertThat(SEEN).isEmpty();

        web.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token").exchange()
                .expectStatus().isOk();
        web.get().uri("/api/v1/charts/admin/overview").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .exchange().expectStatus().isOk();
    }

    @Test
    void everyServiceIsRoutedButKnowledgeServiceIsNot() {
        for (String path : List.of("/api/v1/chat/recent", "/api/v1/chat/send", "/api/v1/files",
                "/api/v1/files/3/download", "/api/v1/charts/me")) {
            web.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer user-token").exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.data.path").isEqualTo(path);
        }
        web.post().uri("/internal/knowledge/retrieve").header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void corsAllowsTheFrontendOriginOnly() {
        web.options().uri("/api/v1/chat/send")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");

        web.options().uri("/api/v1/chat/send")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    private static Jwt jwt(String token, String sub, String role) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(sub).claim("role", role)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
    }
}
