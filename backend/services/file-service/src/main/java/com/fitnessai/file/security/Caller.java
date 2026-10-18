package com.fitnessai.file.security;

import org.springframework.security.oauth2.jwt.Jwt;

public record Caller(long userId, boolean admin, String token) {

    public static Caller from(Jwt jwt) {
        return new Caller(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")),
                jwt.getTokenValue());
    }
}
