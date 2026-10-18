package com.fitnessai.charts.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Redis cache-aside for assembled chart payloads. Results with any
 * unavailable section are never cached, so a recovered service shows up on
 * the next refresh. A Redis outage just means no caching.
 */
@Component
public class ChartCache {

    private static final Logger log = LoggerFactory.getLogger(ChartCache.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public ChartCache(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Map<String, Object> getOrCompute(String key, Duration ttl, Supplier<Map<String, Object>> compute) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return json.readValue(cached, MAP);
            }
        } catch (Exception e) {
            log.debug("Chart cache read skipped: {}", e.getMessage());
        }
        Map<String, Object> fresh = compute.get();
        if (fresh.get("unavailable") instanceof List<?> missing && missing.isEmpty()) {
            try {
                redis.opsForValue().set(key, json.writeValueAsString(fresh), ttl);
            } catch (Exception e) {
                log.debug("Chart cache write skipped: {}", e.getMessage());
            }
        }
        return fresh;
    }
}
