package com.fitnessai.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevocationCheckingJwtDecoderTest {

    private final ReactiveJwtDecoder delegate = mock(ReactiveJwtDecoder.class);
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RevocationCheckingJwtDecoder decoder = new RevocationCheckingJwtDecoder(delegate, redis);

    @Test
    void rejectsRevokedTokens() {
        when(delegate.decode("t")).thenReturn(Mono.just(jwt("jti-1")));
        when(redis.hasKey("jwt:revoked:jti-1")).thenReturn(Mono.just(true));
        StepVerifier.create(decoder.decode("t")).expectError(BadJwtException.class).verify();
    }

    @Test
    void passesValidTokens() {
        when(delegate.decode("t")).thenReturn(Mono.just(jwt("jti-2")));
        when(redis.hasKey("jwt:revoked:jti-2")).thenReturn(Mono.just(false));
        StepVerifier.create(decoder.decode("t")).expectNextCount(1).verifyComplete();
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(delegate.decode("t")).thenReturn(Mono.just(jwt("jti-3")));
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("connection refused")));
        StepVerifier.create(decoder.decode("t")).expectNextCount(1).verifyComplete();
    }

    @Test
    void invalidSignatureNeverReachesRedis() {
        when(delegate.decode("t")).thenReturn(Mono.error(new BadJwtException("bad signature")));
        StepVerifier.create(decoder.decode("t")).expectError(BadJwtException.class).verify();
        verify(redis, never()).hasKey(anyString());
    }

    private static Jwt jwt(String jti) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("7").jti(jti)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
