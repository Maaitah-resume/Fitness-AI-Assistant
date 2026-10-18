package com.fitnessai.user.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Logout blacklist. Key format {@code jwt:revoked:<jti>} is shared with the
 * api-gateway, which checks it before routing — keep the two in sync.
 */
@Component
public class TokenRevocationStore {

    public static final String KEY_PREFIX = "jwt:revoked:";

    private final StringRedisTemplate redis;

    public TokenRevocationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (jti == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
