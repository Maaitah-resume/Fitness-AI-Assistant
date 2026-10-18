package com.fitnessai.knowledge.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * The end user on whose behalf chat-service/file-service is calling, taken
 * from the forwarded JWT. Visibility scopes: "shared" (admin-indexed
 * knowledge everyone can see) and "user:{id}" (that user's private data).
 */
public record Caller(long userId, boolean admin) {

    public static final String SHARED_SCOPE = "shared";

    public static Caller from(Jwt jwt) {
        return new Caller(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }

    public String ownScope() {
        return "user:" + userId;
    }

    public List<String> readableScopes() {
        return List.of(SHARED_SCOPE, ownScope());
    }
}
