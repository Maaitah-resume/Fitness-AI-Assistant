package com.fitnessai.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/**
 * Adds the logout blacklist to normal JWT validation. The gateway is the one
 * place every request passes, so revocation is enforced here. Key format is
 * shared with user-service's TokenRevocationStore: "jwt:revoked:<jti>".
 *
 * Fails open if Redis is unreachable: a Redis outage shouldn't lock every user
 * out, and the token is still signature-, expiry- and issuer-checked.
 */
public class RevocationCheckingJwtDecoder implements ReactiveJwtDecoder {

    static final String KEY_PREFIX = "jwt:revoked:";

    private final ReactiveJwtDecoder delegate;
    private final ReactiveStringRedisTemplate redis;

    public RevocationCheckingJwtDecoder(ReactiveJwtDecoder delegate, ReactiveStringRedisTemplate redis) {
        this.delegate = delegate;
        this.redis = redis;
    }

    @Override
    public Mono<Jwt> decode(String token) {
        return delegate.decode(token).flatMap(jwt -> {
            if (jwt.getId() == null) {
                return Mono.just(jwt);
            }
            return redis.hasKey(KEY_PREFIX + jwt.getId())
                    .onErrorReturn(false)
                    .flatMap(revoked -> revoked
                            ? Mono.error(new BadJwtException("Token has been revoked"))
                            : Mono.just(jwt));
        });
    }
}
