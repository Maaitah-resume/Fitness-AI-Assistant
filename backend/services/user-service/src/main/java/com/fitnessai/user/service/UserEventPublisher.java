package com.fitnessai.user.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tells other services a user is gone so they can drop data they own for it
 * (profile-service, chat-service subscribe). Redis pub/sub is fire-and-forget:
 * a subscriber that's down at that moment misses the event. Good enough until
 * a durable broker is introduced (plan §6 / phase 2+).
 */
@Component
public class UserEventPublisher {

    public static final String USER_DELETED_CHANNEL = "events:user-deleted";

    private final StringRedisTemplate redis;

    public UserEventPublisher(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void userDeleted(long userId) {
        redis.convertAndSend(USER_DELETED_CHANNEL, String.valueOf(userId));
    }
}
